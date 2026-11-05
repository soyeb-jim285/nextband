import { afterEach, beforeEach, expect, it, vi } from 'vitest';
// helpers first: it loads the app, which installs zod .openapi() before settings.ts is evaluated
import { fakeFetch, guestUser, json, req, seedPrompt } from '../test/helpers';
import { generateKeyPairSync } from 'node:crypto';
import { and, eq } from 'drizzle-orm';
import { db } from '../db/client';
import { activityDays, analyses, attempts, cards, mistakes, notificationDevices, notificationLog, user, userSettings } from '../db/schema';
import { setFetch } from '../ai/openrouter';
import { pushSweep, setFcmAccount } from '../push';
import { getSettings } from '../settings';
import { recordActivity } from '../streaks';
import { candidates, nudgeSweep, templateStats } from './planner';
import { isHoldout } from './rules';

const { privateKey } = generateKeyPairSync('rsa', { modulusLength: 2048 });
const ACCOUNT = JSON.stringify({ type: 'service_account', project_id: 'nextband-test', client_email: 'push@nextband-test.iam.gserviceaccount.com', private_key: privateKey.export({ type: 'pkcs8', format: 'pem' }) });
const FCM = 'fcm.googleapis.com/v1/projects/nextband-test/messages:send';
beforeEach(() => setFcmAccount(ACCOUNT));
afterEach(() => {
  setFcmAccount(undefined);
  vi.useRealTimers();
});

// Fixed clocks in the past (rows must be due for pushSweep's SQL now()). Dhaka is UTC+6, no DST. 2026-10-29 is a Thursday.
const at = (local: string) => new Date(`${local}:00+06:00`);
const THU = '2026-10-29';
const DAY = 864e5;

let n = 0;
/** A student with one device, in Dhaka, inside (or outside) the holdout. The device was last seen long ago (no app open resets the back-off). */
async function student(holdout = false, settings: Record<string, unknown> = {}) {
  let id: string;
  do id = `stu-${++n}`;
  while (isHoldout(id) !== holdout);
  await db.insert(user).values({ id, name: 'Rahim', email: `${id}@test.dev` });
  await db.insert(userSettings).values({ userId: id, data: { timeZone: 'Asia/Dhaka', ...settings } });
  await db.insert(notificationDevices).values({ token: `tok-${id}`, userId: id, platform: 'android', locale: 'en-BD', lastSeenAt: new Date('2026-08-01T00:00:00Z') });
  return id;
}
const practised = (userId: string, ...days: string[]) => db.insert(activityDays).values(days.map((day) => ({ userId, day, tests: 1 })));
const logs = (userId: string) => db.select().from(notificationLog).where(eq(notificationLog.userId, userId)).orderBy(notificationLog.createdAt);
/** A nudge already sent at `when`. */
const sent = (userId: string, type: string, when: Date, extra: Partial<typeof notificationLog.$inferInsert> = {}) =>
  db.insert(notificationLog).values({ userId, type, templateId: `${type}-en-1`, dedupeKey: `nudge:${userId}:${when.toISOString()}`, status: 'sent', sentAt: when, scheduledFor: when, ...extra });
function fakeFcm() {
  const f = fakeFetch({ 'oauth2.googleapis.com/token': () => json({ access_token: 'ya29.test', expires_in: 3599 }), [FCM]: () => json({ name: 'm/1' }) });
  setFetch(f);
  return f;
}

it('queues the streak saver once, in the send window of the student zone, and delivers it on the streak channel before quiet hours', async () => {
  const id = await student();
  await practised(id, '2026-10-27', '2026-10-28'); // a 2-day streak, today not yet
  expect(await nudgeSweep(at(`${THU}T19:00`))).toBe(0); // usual time 19:30 (no history): window 19:15–19:45
  expect(await logs(id)).toEqual([]);
  expect(await nudgeSweep(at(`${THU}T19:20`))).toBe(1);
  expect(await nudgeSweep(at(`${THU}T19:35`))).toBe(0); // the same local day: once
  const [row] = await logs(id);
  expect(row).toMatchObject({ type: 'streak', status: 'pending', holdout: false, route: '/streak', dedupeKey: `nudge:${id}:${THU}`, templateId: expect.stringMatching(/^streak-en-/) });
  expect(row!.title).toContain('2');

  vi.useFakeTimers({ toFake: ['Date'] });
  vi.setSystemTime(at(`${THU}T22:00`)); // delivered late (a retry): 30 min left before quiet hours
  const f = fakeFcm();
  expect(await pushSweep()).toBe(1);
  const msg = f.calls.find((c) => c.url.includes(FCM))!.body.message;
  expect(msg.data).toMatchObject({ id: row!.id, uid: id, type: 'streak', channel: 'streak', route: '/streak' });
  expect(msg.android.ttl).toBe('1800s');
  expect((await logs(id))[0]).toMatchObject({ status: 'sent' });
});

it('a nudge that reaches delivery in quiet hours, or after its switch went off, is dropped', async () => {
  const id = await student();
  await practised(id, '2026-10-27', '2026-10-28');
  expect(await nudgeSweep(at(`${THU}T19:30`))).toBe(1);
  vi.useFakeTimers({ toFake: ['Date'] });
  vi.setSystemTime(at(`${THU}T23:10`));
  const f = fakeFcm();
  expect(await pushSweep()).toBe(0);
  expect(f.calls.filter((c) => c.url.includes(FCM))).toEqual([]);
  expect((await logs(id))[0]).toMatchObject({ status: 'skipped' });
});

it('the holdout never gets a nudge: a holdout row records what it would have got, and practice within 2 h marks it like a sent one', async () => {
  const h = await student(true);
  const p = await student(false);
  for (const id of [h, p]) await practised(id, '2026-10-27', '2026-10-28');
  expect(await nudgeSweep(at(`${THU}T19:30`))).toBe(1); // only p's is queued
  expect(await logs(h)).toMatchObject([{ type: 'streak', status: 'skipped', holdout: true, title: null }]);
  await db.update(notificationLog).set({ status: 'sent', sentAt: at(`${THU}T19:30`) }).where(eq(notificationLog.userId, p));
  await recordActivity(h, { drills: 1 }, at(`${THU}T21:00`));
  await recordActivity(p, { drills: 1 }, at(`${THU}T21:40`)); // 2 h 10 min after: not acted
  expect((await logs(h))[0]!.actedAt).toEqual(at(`${THU}T21:00`));
  expect((await logs(p))[0]!.actedAt).toBeNull();
  await recordActivity(p, { drills: 1 }, at(`${THU}T21:20`));
  expect((await logs(p))[0]!.actedAt).toEqual(at(`${THU}T21:20`));
});

it('caps: 5 nudges in the last 7 days stop a 6th; a switched-off type is never planned', async () => {
  const capped = await student();
  const off = await student(false, { notifyStreak: false });
  for (const id of [capped, off]) await practised(id, '2026-10-27', '2026-10-28');
  for (const d of [23, 24, 25, 26, 28]) await sent(capped, 'drills', at(`2026-10-${d}T19:30`), { openedAt: at(`2026-10-${d}T19:40`) });
  expect(await nudgeSweep(at(`${THU}T19:30`))).toBe(0);
  expect((await logs(capped)).filter((r) => r.status === 'pending')).toEqual([]);
  expect(await logs(off)).toEqual([]);
  // the next day, once the 23rd's send is more than 7 days old, the streak saver goes again
  await practised(capped, THU);
  expect(await nudgeSweep(at('2026-10-30T19:25'))).toBe(0);
  expect(await nudgeSweep(at('2026-10-30T19:35'))).toBe(1);
});

it('back-off: 8 ignored in a row sends the goodbye, then only the exam countdown; opening the app starts over', async () => {
  const id = await student(false, { examDate: '2026-11-05' }); // 7 days after THU
  await db.insert(cards).values(Array.from({ length: 6 }, (_, i) => ({ userId: id, front: `f${i}`, back: 'b', source: 'mistake', due: new Date('2026-10-01T00:00:00Z') })));
  for (let d = 9; d <= 16; d++) await sent(id, 'drills', new Date(at(`${THU}T19:30`).getTime() - d * DAY));
  // THU − 1 is not an exam countdown day: level 3 sends the goodbye first, whatever else applies
  expect(await nudgeSweep(at('2026-10-28T19:30'))).toBe(1);
  expect((await logs(id)).at(-1)).toMatchObject({ type: 'goodbye', status: 'pending' });
  await db.update(notificationLog).set({ status: 'sent', sentAt: at('2026-10-28T19:30') }).where(and(eq(notificationLog.userId, id), eq(notificationLog.type, 'goodbye')));
  expect(await nudgeSweep(at(`${THU}T19:30`))).toBe(1); // exam in 7 days: still allowed; the 6 due drills are not
  expect((await logs(id)).at(-1)).toMatchObject({ type: 'exam', route: '/mock' });
  await db.update(notificationLog).set({ status: 'sent', sentAt: at(`${THU}T19:30`) }).where(eq(notificationLog.status, 'pending'));
  expect(await nudgeSweep(at('2026-10-30T14:30'))).toBe(0); // Friday: level 3, nothing allowed (and no exam day)
  // the student opens the app: level 0 again, so the due drills come back (it's past Jumuah)
  await db.update(notificationDevices).set({ lastSeenAt: at('2026-10-31T09:00') }).where(eq(notificationDevices.userId, id));
  expect(await nudgeSweep(at('2026-10-31T19:30'))).toBe(1);
  expect((await logs(id)).at(-1)).toMatchObject({ type: 'drills', route: '/improve/drill' });
});

it("Jumu'ah: a Friday reminder time inside 12:30–14:00 moves out of it", async () => {
  const id = await student(false, { remindAt: '13:00' });
  await practised(id, '2026-10-28', THU);
  expect(await nudgeSweep(at('2026-10-30T13:00'))).toBe(0);
  expect(await nudgeSweep(at('2026-10-30T12:15'))).toBe(1);
  expect((await logs(id))[0]).toMatchObject({ type: 'streak' });
});

it('finds the candidates: drills due, a pattern, the exam countdown, a cold skill, win-back', async () => {
  const id = await student(false, { examDate: '2026-11-12', remindAt: '19:30' }); // 14 days
  const now = at(`${THU}T19:30`);
  await db.insert(cards).values(Array.from({ length: 5 }, (_, i) => ({ userId: id, front: `f${i}`, back: 'b', source: 'mistake', due: new Date('2026-10-01T00:00:00Z') })));
  const w = await seedPrompt({ skill: 'writing', part: 2, type: 'opinion' });
  const s = await seedPrompt({ skill: 'speaking', part: 2 });
  // 4 essays this week, articles wrong in 3 of them
  for (let i = 0; i < 4; i++) {
    const [a] = await db.insert(attempts).values({ userId: id, promptId: w.id, skill: 'writing', part: 2, text: 'x', status: 'done', createdAt: new Date(now.getTime() - (i + 1) * 3_600_000) }).returning();
    await db.insert(analyses).values({ attemptId: a!.id, result: { errors: [] }, overall: 6.5, criteria: {}, models: {} });
    if (i < 3) await db.insert(mistakes).values({ userId: id, attemptId: a!.id, errorId: 'e', category: 'grammar.article', original: 'a', correction: 'the', explanation: 'x' });
  }
  // speaking last done 10 days ago
  const [sp] = await db.insert(attempts).values({ userId: id, promptId: s.id, skill: 'speaking', part: 2, status: 'done', createdAt: new Date(now.getTime() - 10 * DAY) }).returning();
  await db.insert(analyses).values({ attemptId: sp!.id, result: { errors: [] }, overall: 6, criteria: {}, models: {} });
  await practised(id, '2026-10-26'); // nothing for 3 days
  const settings = { ...(await getSettings(id)), timeZone: 'Asia/Dhaka' };
  const c = await candidates(id, settings, 'Asia/Dhaka', now);
  expect(c.map((x) => x.type).sort()).toEqual(['cold', 'drills', 'exam', 'pattern', 'winback']);
  expect(c.find((x) => x.type === 'pattern')).toMatchObject({ vars: { pattern: 'Articles', skill: 'Writing', count: '3', of: '4' }, route: '/improve/drill?pattern=grammar.article' });
  expect(c.find((x) => x.type === 'cold')).toMatchObject({ vars: { skill: 'Speaking', days: '10', band: '6.0' }, route: '/speaking' });
  expect(c.find((x) => x.type === 'exam')!.vars).toEqual({ days: '14' });
  expect(c.find((x) => x.type === 'winback')!.vars).toMatchObject({ days: '3', band: '6.5' });
  expect(c.find((x) => x.type === 'drills')!.vars).toEqual({ due: '5' });
  // P1 pattern and exam beat the rest; with both fresh the first listed wins
  expect(await nudgeSweep(now)).toBe(1);
  expect(['pattern', 'exam']).toContain((await logs(id))[0]!.type);
});

it('bandit counts: sent, not holdout, at least 2 h old; wins are the ones practised after', async () => {
  const id = await student();
  const now = at(`${THU}T19:30`);
  const old = new Date(now.getTime() - 3 * 3_600_000);
  await sent(id, 'drills', old, { templateId: 'drills-en-1', actedAt: now });
  await sent(id, 'drills', new Date(old.getTime() - DAY), { templateId: 'drills-en-1' });
  await sent(id, 'drills', new Date(now.getTime() - 60_000), { templateId: 'drills-en-1' }); // too recent to judge
  await sent(id, 'drills', old, { templateId: 'drills-en-2', holdout: true });
  await sent(id, 'result', old, { templateId: 'result-en-1' }); // results aren't nudges
  expect(Object.fromEntries(await templateStats(now))).toEqual({ 'drills-en-1': { sends: 2, wins: 1 } });
});

it('GET /api/streak tells the app when to remind: 19:30 by default', async () => {
  const g = await guestUser();
  const r = await req('/api/streak', { headers: g.headers });
  expect(r.status).toBe(200);
  expect(((await r.json()) as { remindAt: string }).remindAt).toBe('19:30');
});
