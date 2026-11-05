import { afterEach, beforeEach, expect, it } from 'vitest';
// helpers first: it loads the app, which installs zod .openapi() before settings.ts is evaluated
import { chatReply, fakeFetch, guestUser, json, req, seedPrompt, testUser } from './test/helpers';
import { generateKeyPairSync, verify } from 'node:crypto';
import { eq, sql } from 'drizzle-orm';
import { db } from './db/client';
import { analyses, attempts, notificationDevices, notificationLog } from './db/schema';
import { setFetch } from './ai/openrouter';
import { speakingLlm, sttWords } from './ai/fixtures';
import { recoverStale, runAnalysis } from './jobs';
import { pushSweep, queueResult, setFcmAccount } from './push';
import { storage } from './storage';

const { privateKey, publicKey } = generateKeyPairSync('rsa', { modulusLength: 2048 });
const ACCOUNT = JSON.stringify({ type: 'service_account', project_id: 'nextband-test', client_email: 'push@nextband-test.iam.gserviceaccount.com', private_key: privateKey.export({ type: 'pkcs8', format: 'pem' }) });
const FCM = 'fcm.googleapis.com/v1/projects/nextband-test/messages:send';

beforeEach(() => setFcmAccount(ACCOUNT));
afterEach(() => setFcmAccount(undefined));

/** FCM that answers `fcm` (default 200) and Google's token endpoint. */
function fakeFcm(fcm: () => Response = () => json({ name: 'projects/nextband-test/messages/1' }), extra: Parameters<typeof fakeFetch>[0] = {}) {
  const f = fakeFetch({ 'oauth2.googleapis.com/token': () => json({ access_token: 'ya29.test', expires_in: 3599, token_type: 'Bearer' }), [FCM]: fcm, ...extra });
  setFetch(f);
  return f;
}
const fcmCalls = (f: ReturnType<typeof fakeFetch>) => f.calls.filter((c) => c.url.includes(FCM));

async function device(headers: Headers, token = `tok-${Math.random()}`, extra: object = {}) {
  expect((await req('/api/push/token', { method: 'PUT', headers, body: { token, platform: 'android', appVersion: '2.0.0', locale: 'en-BD', ...extra } })).status).toBe(200);
  return token;
}
const devices = (userId: string) => db.select().from(notificationDevices).where(eq(notificationDevices.userId, userId));
const logs = (userId: string) => db.select().from(notificationLog).where(eq(notificationLog.userId, userId));
const due = () => db.update(notificationLog).set({ scheduledFor: sql`now() - interval '1 second'` }).where(eq(notificationLog.status, 'pending'));

/** A marked writing attempt of `userId` with band `overall`. */
async function marked(userId: string, overall: number, extra: Partial<typeof attempts.$inferInsert> = {}) {
  const p = await seedPrompt({ skill: 'writing', part: 2, type: 'opinion' });
  const [a] = await db.insert(attempts).values({ userId, promptId: p.id, skill: 'writing', part: 2, text: 'x', status: 'done', ...extra }).returning();
  await db.insert(analyses).values({ attemptId: a!.id, result: { errors: [] }, overall, criteria: {}, models: {} });
  return a!;
}

it('registers, refreshes, moves and forgets a device token', async () => {
  const a = await testUser();
  const b = await testUser();
  expect((await req('/api/push/token', { method: 'PUT', body: { token: 't', platform: 'android' } })).status).toBe(401);
  expect((await req('/api/push/token', { method: 'PUT', headers: a.headers, body: { token: 't', platform: 'desktop' } })).status).toBe(400);
  await device(a.headers, 'shared');
  await device(a.headers, 'shared', { appVersion: '2.0.1' }); // idempotent refresh
  expect(await devices(a.user.id)).toMatchObject([{ token: 'shared', kind: 'token', platform: 'android', appVersion: '2.0.1', locale: 'en-BD', disabledAt: null }]);
  await db.update(notificationDevices).set({ disabledAt: new Date() });
  await device(b.headers, 'shared', { kind: 'fid' }); // b signed in on the same phone: the token is b's now, and on again
  expect(await devices(a.user.id)).toEqual([]);
  expect(await devices(b.user.id)).toMatchObject([{ token: 'shared', kind: 'fid', disabledAt: null }]);
  await req('/api/push/token', { method: 'DELETE', headers: a.headers, body: { token: 'shared' } }); // not a's: untouched
  expect(await devices(b.user.id)).toHaveLength(1);
  expect((await req('/api/push/token', { method: 'DELETE', headers: b.headers, body: { token: 'shared' } })).status).toBe(200);
  expect(await devices(b.user.id)).toEqual([]);
});

it('keeps at most 10 devices per student, dropping the least recently seen', async () => {
  const { headers, user } = await testUser();
  for (let i = 0; i < 12; i++) await device(headers, `t${i}`);
  const left = (await devices(user.id)).map((d) => d.token);
  expect(left).toHaveLength(10);
  expect(left).not.toContain('t0');
});

it('a guest who signs up keeps its devices and push log; deleting a guest deletes them', async () => {
  const g = await guestUser();
  await device(g.headers, 'g-phone');
  await db.insert(notificationLog).values({ userId: g.user.id, type: 'test', dedupeKey: 'test:1', status: 'sent' });
  const res = await req('/api/auth/sign-up/email', { headers: g.headers, body: { email: `g${Date.now()}@test.dev`, password: 'password1234', name: 'G' } });
  const newId = ((await res.json()) as { user: { id: string } }).user.id;
  expect(await devices(newId)).toMatchObject([{ token: 'g-phone' }]);
  expect(await logs(newId)).toHaveLength(1);

  const g2 = await guestUser();
  await device(g2.headers, 'g2-phone');
  await db.insert(notificationLog).values({ userId: g2.user.id, type: 'test', dedupeKey: 'test:2', status: 'sent' });
  expect((await req('/api/me/guest', { method: 'DELETE', headers: g2.headers })).status).toBe(200);
  expect(await db.select().from(notificationDevices).where(eq(notificationDevices.token, 'g2-phone'))).toEqual([]);
  expect(await logs(g2.user.id)).toEqual([]);
});

it('marking an attempt queues a result push for 2 minutes; the FCM v1 message is signed, data-only and points at the result', async () => {
  const f = fakeFcm(undefined, {
    '/audio/transcriptions': () => json(sttWords),
    '/chat/completions': (_: string, init: RequestInit) => {
      // as jobs.test.ts speakingChat: bands fc 7, lr 6, gra 6, p 6 → 6.5
      const body = JSON.parse(String(init.body));
      if (body.response_format?.json_schema?.name !== 'criterion_score') return chatReply(speakingLlm);
      const band = ({ fc: 7, lr: 6, gra: 6, p: 6 } as Record<string, number>)[body.messages[1].content.match(/<criterion id="(\w+)"/)[1]]!;
      return chatReply({ checks: [], evidence: [], descriptor: '', summary: '', injection: false, band });
    },
  });
  const { headers, user } = await testUser();
  const token = await device(headers);
  const p = await seedPrompt();
  const [a] = await db.insert(attempts).values({ userId: user.id, promptId: p.id, skill: 'speaking', part: 1, audioKey: `audio/${user.id}/x.webm`, durationMs: 3000, marks: [0, 1200], status: 'analyzing' }).returning();
  await storage.put(a!.audioKey!, new Uint8Array([1]), 'audio/webm');
  await runAnalysis(a!.id);
  const [row] = await logs(user.id);
  expect(row).toMatchObject({ type: 'result', dedupeKey: `result:${a!.userId}:${a!.id}`, status: 'pending' });
  expect(row!.scheduledFor.getTime() - Date.now()).toBeGreaterThan(60_000); // not before 2 minutes
  expect(await pushSweep()).toBe(0);

  await due();
  expect(await pushSweep()).toBe(1);
  const [tokenCall] = f.calls.filter((c) => c.url.includes('oauth2.googleapis.com/token'));
  const form = new URLSearchParams(tokenCall!.body);
  expect(form.get('grant_type')).toBe('urn:ietf:params:oauth:grant-type:jwt-bearer');
  const [h, claims, sig] = form.get('assertion')!.split('.');
  expect(JSON.parse(Buffer.from(h!, 'base64url').toString())).toEqual({ alg: 'RS256', typ: 'JWT' });
  expect(JSON.parse(Buffer.from(claims!, 'base64url').toString())).toMatchObject({ iss: 'push@nextband-test.iam.gserviceaccount.com', scope: 'https://www.googleapis.com/auth/firebase.messaging', aud: 'https://oauth2.googleapis.com/token' });
  expect(verify('sha256', Buffer.from(`${h}.${claims}`), publicKey, Buffer.from(sig!, 'base64url'))).toBe(true);

  const [send] = fcmCalls(f);
  expect(send!.headers.authorization).toBe('Bearer ya29.test');
  const m = send!.body.message;
  expect(m.token).toBe(token);
  expect(m.notification).toBeUndefined();
  expect(m.android).toEqual({ priority: 'HIGH', ttl: '86400s', collapse_key: 'result' });
  expect(Object.keys(m.data).sort()).toEqual(['body', 'channel', 'id', 'route', 'title', 'type', 'uid']);
  expect(m.data).toMatchObject({ id: row!.id, uid: a!.userId, type: 'result', channel: 'results', route: `/speaking/result/${a!.id}` });
  expect(`${m.data.title} ${m.data.body}`).toMatch(/Speaking Part 1.*6\.5|6\.5.*Speaking Part 1/s);
  expect((await logs(user.id))[0]).toMatchObject({ status: 'sent', title: m.data.title, body: m.data.body, route: m.data.route });
  expect((await logs(user.id))[0]!.sentAt).not.toBeNull();

  // open tracking: only the owner, first open wins
  const other = await testUser();
  await req('/api/push/opened', { headers: other.headers, body: { id: row!.id } });
  expect((await logs(user.id))[0]!.openedAt).toBeNull();
  expect((await req('/api/push/opened', { headers, body: { id: row!.id } })).status).toBe(200);
  expect((await logs(user.id))[0]!.openedAt).not.toBeNull();
});

it('a result the student already opened in the app is not pushed', async () => {
  const f = fakeFcm();
  const { headers, user } = await testUser();
  await device(headers);
  const a = await marked(user.id, 6);
  await queueResult(a);
  await req(`/api/attempts/${a.id}/status`, { headers });
  await due();
  expect(await pushSweep()).toBe(0);
  expect(fcmCalls(f)).toHaveLength(0);
  expect((await logs(user.id))[0]!.status).toBe('cancelled');
});

it('a new best band gets the personal-best copy; a failed marking gets the failed copy; the results switch silences both', async () => {
  const f = fakeFcm();
  const { headers, user } = await testUser();
  await device(headers);
  await marked(user.id, 6);
  const best = await marked(user.id, 7);
  await queueResult(best);
  await due();
  await pushSweep();
  expect(fcmCalls(f)[0]!.body.message.data).toMatchObject({ type: 'best', route: `/writing/result/${best.id}` });
  expect(fcmCalls(f)[0]!.body.message.data.title + fcmCalls(f)[0]!.body.message.data.body).toContain('7.0');

  const p = await seedPrompt({ skill: 'writing', part: 1, type: 'line' });
  const [bad] = await db.insert(attempts).values({ userId: user.id, promptId: p.id, skill: 'writing', part: 1, text: 'x', status: 'analyzing', updatedAt: new Date(Date.now() - 3_600_000) }).returning();
  await db.execute(sql`update attempts set updated_at = now() - interval '1 hour' where id = ${bad!.id}`);
  await recoverStale(); // stuck analysis → failed, and its push queued
  await due();
  await pushSweep();
  expect(fcmCalls(f)[1]!.body.message.data).toMatchObject({ type: 'failed', route: `/writing/result/${bad!.id}` });
  expect(fcmCalls(f)[1]!.body.message.data.title + fcmCalls(f)[1]!.body.message.data.body).toContain('Writing Task 1');

  expect((await req('/api/settings', { method: 'PUT', headers, body: { notifyResults: false } })).status).toBe(200);
  await queueResult(await marked(user.id, 5));
  await due();
  expect(await pushSweep()).toBe(0);
  expect(fcmCalls(f)).toHaveLength(2);
});

it('a session pushes once, after its last part, with the session band', async () => {
  const f = fakeFcm();
  const { headers, user } = await testUser();
  await device(headers);
  const sessionId = crypto.randomUUID();
  const t1 = await marked(user.id, 6, { part: 1, sessionId });
  const t2 = await marked(user.id, 7, { part: 2, sessionId, status: 'analyzing' });
  await queueResult(t1);
  await due();
  expect(await pushSweep()).toBe(0); // task 2 still being marked: wait
  expect((await logs(user.id))[0]!.status).toBe('pending');
  await db.update(attempts).set({ status: 'done' }).where(eq(attempts.id, t2.id));
  await queueResult(t2);
  expect(await logs(user.id)).toHaveLength(1); // one pending row per session
  await due();
  expect(await pushSweep()).toBe(1);
  const d = fcmCalls(f)[0]!.body.message.data;
  expect(d.route).toBe(`/writing/result/${t1.id}?pair=${t2.id}`);
  expect(d.title + d.body).toContain('6.5'); // (6 + 2×7) / 3 = 6.67 → 6.5
});

it('FCM errors: UNREGISTERED disables the token, 503 retries later, and the access token is reused', async () => {
  const { headers, user } = await testUser();
  const dead = await device(headers, 'dead');
  let status = 404;
  const f = fakeFcm(() =>
    status === 404
      ? json({ error: { code: 404, status: 'NOT_FOUND', message: 'Requested entity was not found.', details: [{ '@type': 'type.googleapis.com/google.firebase.fcm.v1.FcmError', errorCode: 'UNREGISTERED' }] } }, 404)
      : new Response(JSON.stringify({ error: { code: 503, status: 'UNAVAILABLE' } }), { status: 503, headers: { 'Retry-After': '120' } }),
  );
  expect((await req('/api/push/test', { method: 'POST', headers })).status).toBe(200);
  expect((await devices(user.id))[0]).toMatchObject({ token: dead });
  expect((await devices(user.id))[0]!.disabledAt).not.toBeNull();
  expect((await logs(user.id))[0]!.status).toBe('failed');
  expect((await req('/api/push/test', { method: 'POST', headers })).status).toBe(409); // no live device left

  await device(headers, 'dead'); // re-registered: on again
  status = 503;
  await db.delete(notificationLog);
  const r = await req('/api/push/test', { method: 'POST', headers });
  expect(await r.json()).toEqual({ sent: false });
  const [row] = await logs(user.id);
  expect(row!.status).toBe('pending');
  expect(row!.scheduledFor.getTime() - Date.now()).toBeGreaterThan(110_000); // Retry-After 120 s beats the 1-minute floor
  expect((await req('/api/push/test', { method: 'POST', headers })).status).toBe(429); // one test per 30 s
  expect(f.calls.filter((c) => c.url.includes('oauth2')).length).toBe(1); // cached across sends
});

it('without FCM_SERVICE_ACCOUNT_JSON push is a clean no-op', async () => {
  setFcmAccount(undefined);
  const f = fakeFcm();
  const { headers, user } = await testUser();
  await device(headers); // the app can still register
  await queueResult(await marked(user.id, 6));
  expect(await logs(user.id)).toEqual([]);
  expect(await pushSweep()).toBe(0);
  expect((await req('/api/push/test', { method: 'POST', headers })).status).toBe(503);
  expect(f.calls).toHaveLength(0);
});

it('guests may change only the notification settings', async () => {
  const g = await guestUser();
  const ok = await req('/api/settings', { method: 'PUT', headers: g.headers, body: { notifyResults: false, quietStart: '23:00', notifyLanguage: 'banglish' } });
  expect(ok.status).toBe(200);
  expect(await ok.json()).toMatchObject({ notifyResults: false, quietStart: '23:00', notifyLanguage: 'banglish', notifyStreak: true, remindAt: null });
  expect((await req('/api/settings', { method: 'PUT', headers: g.headers, body: { targetBand: 8 } })).status).toBe(403);
  expect((await req('/api/settings', { method: 'PUT', headers: g.headers, body: { quietEnd: '25:00' } })).status).toBe(400);
});
