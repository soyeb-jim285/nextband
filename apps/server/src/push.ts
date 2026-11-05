// Push notifications through FCM HTTP v1 (docs/engagement/notifications-and-streaks.md §4, §5). Round B: the P0 types (#1 result ready, #2 marking
// failed, #10 new personal best) and the Settings test push. Every push is a notification_log row: queued as `pending`, delivered by pushSweep.
//
// FCM: https://firebase.google.com/docs/reference/fcm/rest/v1/projects.messages. Messages are data-only: the app builds the notification itself
// (its channel, action buttons, open tracking), so onMessageReceived runs in the background too. The data payload is
// { id, type, channel, title, body, route } (all strings).
import { checkPush, PUSH_TYPE_CHANNEL, renderPush, sessionBand, weightedWritingBand, type PushLanguage, type PushType } from '@ielts/core';
import { and, desc, eq, gt, gte, inArray, isNull, lte, notInArray, or, sql } from 'drizzle-orm';
import { sign } from 'node:crypto';
import { httpFetch } from './ai/openrouter';
import type { AnalysisResult } from './ai/types';
import { db } from './db/client';
import { analyses, attempts, mockExams, notificationDevices, notificationLog, userSettings } from './db/schema';
import { env } from './env';
import { isNudge, NUDGE_CHANNEL, NUDGE_TYPES, nudgeTtl, zoneIn, type NudgeType } from './push/rules';
import { getSettings, mergeSettings } from './settings';

type Account = { project_id: string; client_email: string; private_key: string };
const parseAccount = (json: string | undefined): Account | null => {
  if (!json) return null;
  try {
    const a = JSON.parse(json) as Account;
    if (a.project_id && a.client_email && a.private_key) return a;
  } catch {}
  console.error('[push] FCM_SERVICE_ACCOUNT_JSON is not a service account key: push is off');
  return null;
};
let account = parseAccount(env.FCM_SERVICE_ACCOUNT_JSON);
let warned = false;
/** The service account, or null (push off: every send is a no-op, logged once). */
function fcm(): Account | null {
  if (!account && !warned) {
    warned = true;
    console.warn('[push] FCM_SERVICE_ACCOUNT_JSON is not set: push notifications are off');
  }
  return account;
}
/** Test injection: a service account JSON, or undefined to turn push off. */
export function setFcmAccount(json: string | undefined) {
  account = parseAccount(json);
  cachedToken = undefined;
}
export const pushEnabled = () => !!fcm();

// ---------- OAuth2 access token: a service-account JWT (RS256) exchanged at Google's token endpoint, cached until a minute before it expires ----------
// https://developers.google.com/identity/protocols/oauth2/service-account#httprest
const TOKEN_URL = 'https://oauth2.googleapis.com/token';
const SCOPE = 'https://www.googleapis.com/auth/firebase.messaging';
let cachedToken: { token: string; until: number } | undefined;

async function accessToken(a: Account): Promise<string> {
  if (cachedToken && cachedToken.until > Date.now()) return cachedToken.token;
  const now = Math.floor(Date.now() / 1000);
  const part = (o: object) => Buffer.from(JSON.stringify(o)).toString('base64url');
  const unsigned = `${part({ alg: 'RS256', typ: 'JWT' })}.${part({ iss: a.client_email, scope: SCOPE, aud: TOKEN_URL, iat: now, exp: now + 3600 })}`;
  const jwt = `${unsigned}.${sign('sha256', Buffer.from(unsigned), a.private_key).toString('base64url')}`;
  const r = await httpFetch(TOKEN_URL, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({ grant_type: 'urn:ietf:params:oauth:grant-type:jwt-bearer', assertion: jwt }).toString(),
  });
  if (!r.ok) throw new Error(`FCM token exchange failed: HTTP ${r.status}`);
  const j = (await r.json()) as { access_token: string; expires_in: number };
  cachedToken = { token: j.access_token, until: Date.now() + (j.expires_in - 60) * 1000 };
  return j.access_token;
}

// ---------- One message to one device ----------
type Device = typeof notificationDevices.$inferSelect;
export type Push = { id: string; type: PushType | NudgeType; title: string; body: string; route: string; ttl?: number };
type Outcome = 'sent' | 'dead' | 'error' | { retryMs: number };
const TTL: Record<PushType, string> = { result: '86400s', failed: '86400s', best: '86400s', test: '3600s' };

/** https://firebase.google.com/docs/reference/fcm/rest/v1/ErrorCode: UNREGISTERED (404) / SENDER_ID_MISMATCH (403) = this token is dead.
 *  INVALID_ARGUMENT (400) is a dead token only when it names the token (manage-tokens: "only if the payload is completely valid"); any other
 *  400 is our message's fault and must not switch off the student's devices. QUOTA_EXCEEDED (429) and 5xx = retry with exponential backoff
 *  from 1 minute, honouring Retry-After. */
async function sendOne(a: Account, d: Device, p: Push, tries: number): Promise<Outcome> {
  const r = await httpFetch(`https://fcm.googleapis.com/v1/projects/${encodeURIComponent(a.project_id)}/messages:send`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${await accessToken(a)}`, 'Content-Type': 'application/json' },
    body: JSON.stringify({
      message: {
        [d.kind === 'fid' ? 'fid' : 'token']: d.token,
        // uid: the app drops a message meant for an account that is no longer signed in on this phone (a sign-out whose token delete never reached us)
        data: { id: p.id, uid: d.userId, type: p.type, channel: isNudge(p.type) ? NUDGE_CHANNEL[p.type] : PUSH_TYPE_CHANNEL[p.type], title: p.title, body: p.body, route: p.route },
        // no collapse_key: FCM keeps only the newest message per key for an offline phone, and every result must arrive
        android: { priority: 'HIGH', ttl: p.ttl ? `${p.ttl}s` : TTL[p.type as PushType] },
      },
    }),
  });
  if (r.ok) return 'sent';
  const err = ((await r.json().catch(() => null)) as { error?: { status?: string; message?: string; details?: { '@type'?: string; errorCode?: string }[] } } | null)?.error;
  const code = err?.details?.find((x) => x['@type']?.endsWith('google.firebase.fcm.v1.FcmError'))?.errorCode ?? err?.status;
  if (r.status === 404 || code === 'UNREGISTERED' || code === 'SENDER_ID_MISMATCH' || (r.status === 400 && /token|fid|installation/i.test(err?.message ?? ''))) {
    console.warn('[push] dropping token', d.platform, r.status, code, err?.message?.slice(0, 200));
    return 'dead';
  }
  if (r.status === 401) cachedToken = undefined; // a revoked or expired access token: fetch a new one on the retry
  if (r.status === 401 || r.status === 429 || r.status >= 500) {
    const after = Number(r.headers.get('Retry-After')) * 1000 || 0;
    return { retryMs: Math.max(after, 60_000 * 2 ** (tries - 1)) };
  }
  console.error('[push] FCM send failed', r.status, code, err?.message?.slice(0, 200));
  return 'error';
}

// ---------- The queue ----------
const MAX_TRIES = 3;
const LEASE = sql`now() + interval '5 minutes'`; // a claimed row a crashed process never finished is due again after this
const RESULT_DELAY = sql`now() + interval '2 minutes'`; // §4 #1: only when the student hasn't seen the result within 2 minutes
type Row = typeof notificationLog.$inferSelect;
/** One student's result row: the user id is part of the key, so another account's session id can never touch it. */
export const resultKey = (userId: string, ref: string) => `result:${userId}:${ref}`;

const activeDevices = (userId: string) => db.select().from(notificationDevices).where(and(eq(notificationDevices.userId, userId), isNull(notificationDevices.disabledAt))).orderBy(desc(notificationDevices.lastSeenAt));

/** An attempt (or every part of its session) was marked or failed: queue #1/#2/#10 for 2 minutes from now. Called BEFORE the attempt's status
 *  changes, so a client that sees the result always finds the row to cancel (markSeen). Only for students with a device. Never throws. */
export async function queueResult(a: { id: string; userId: string; sessionId: string | null }): Promise<void> {
  try {
    if (!fcm()) return;
    // the attempt's owner now, not a.userId: a guest may have signed up while it was marked (linkGuest moved it)
    // ponytail: a link landing between this read and the insert loses the push (FK on the deleted guest); a one-statement insert…select if that matters
    const userId = (await db.select({ userId: attempts.userId }).from(attempts).where(eq(attempts.id, a.id)))[0]?.userId ?? a.userId;
    if (!(await activeDevices(userId)).length) return;
    await db
      .insert(notificationLog)
      .values({ userId, type: 'result', dedupeKey: resultKey(userId, a.sessionId ?? a.id), scheduledFor: RESULT_DELAY })
      .onConflictDoUpdate({ target: notificationLog.dedupeKey, targetWhere: sql`status = 'pending'`, set: { scheduledFor: RESULT_DELAY } }); // the next part of a session pushes it back
  } catch (e) {
    console.error('[push] queueResult failed', a.id, e);
  }
}

/** The student opened a finished result in the app: its push is not needed. `ref` = the attempt's sessionId, else its id. Nothing is cancelled
 *  while a part of that session is still being marked (the push then reports the whole session). */
export async function markSeen(userId: string, ref: string | null | undefined): Promise<void> {
  if (!ref) return;
  await db
    .update(notificationLog)
    .set({ status: 'cancelled' })
    .where(and(eq(notificationLog.userId, userId), eq(notificationLog.dedupeKey, resultKey(userId, ref)), eq(notificationLog.status, 'pending'),
      sql`not exists (select 1 from attempts where user_id = ${userId} and (id = ${ref} or session_id = ${ref}) and status = 'analyzing')`))
    .catch((e) => console.error('[push] markSeen failed', e));
}

const LABEL = { writing: 'Writing', speaking: 'Speaking' } as const;
type Content = { type: PushType; vars: Record<string, string>; route: string } | 'wait' | null;

/** What a result row says now, from the attempts' current state: null = nothing to say, 'wait' = a part is still being marked or recorded. */
async function resultContent(row: Row): Promise<Content> {
  const ref = row.dedupeKey.slice(resultKey(row.userId, '').length);
  const parts = await db.select().from(attempts).where(and(eq(attempts.userId, row.userId), or(eq(attempts.id, ref), eq(attempts.sessionId, ref)))).orderBy(attempts.part);
  // a part still being marked, or recorded in the last 30 minutes (the student is mid-test): wait for it. Older recordings were abandoned.
  if (parts.some((p) => p.status === 'analyzing' || (p.status === 'recording' && p.updatedAt.getTime() > Date.now() - 30 * 60_000))) return 'wait';
  const ended = parts.filter((p) => p.status === 'done' || p.status === 'failed');
  const first = ended[0];
  if (!first) return null;
  const skill = first.skill;
  const mock = first.sessionId
    ? await db.query.mockExams.findFirst({ where: and(eq(mockExams.userId, row.userId), or(eq(mockExams.writingSessionId, first.sessionId), eq(mockExams.speakingSessionId, first.sessionId))), columns: { id: true } })
    : undefined;
  const what = (p: typeof first) => (skill === 'writing' ? `Writing Task ${p.part}` : `Speaking Part ${p.part}`);
  const failed = ended.find((p) => p.status === 'failed');
  if (failed) return { type: 'failed', vars: { what: what(failed) }, route: `/${skill}/result/${failed.id}` };

  const rows = await db.select({ attemptId: analyses.attemptId, overall: analyses.overall, result: analyses.result }).from(analyses).where(inArray(analyses.attemptId, ended.map((p) => p.id)));
  const marked = ended.filter((p) => rows.some((r) => r.attemptId === p.id && !(r.result as AnalysisResult).noSpeech && !(r.result as AnalysisResult).tooShort));
  if (!marked.length) return null; // nothing heard / too short: the result page says so, no score to announce
  const band = (id: string) => rows.find((r) => r.attemptId === id)!.overall;
  const t = (n: number) => marked.find((p) => p.part === n);
  const score =
    marked.length === 1 ? band(marked[0]!.id)
    : skill === 'writing' ? (t(1) && t(2) ? weightedWritingBand(band(t(1)!.id), band(t(2)!.id)) : band(marked[0]!.id))
    : sessionBand(marked.map((p) => band(p.id)));
  const route = mock
    ? `/mock/${mock.id}`
    : skill === 'writing'
      ? `/writing/result/${marked[0]!.id}${marked[1] ? `?pair=${marked[1].id}` : ''}`
      : `/speaking/result/${marked[0]!.id}${first.sessionId ? `?session=${first.sessionId}` : ''}`;
  const vars = { what: marked.length > 1 ? `${LABEL[skill]} test` : what(marked[0]!), band: score.toFixed(1), skill: LABEL[skill] };
  // #10 merged into #1: a new best band in this skill (beats every earlier marked attempt; there must be one) gets the personal-best copy
  // ponytail: a session's band is compared with single parts' bands; a per-session history if that ever reads wrong
  const [prev] = await db
    .select({ best: sql<number | null>`max(${analyses.overall})` })
    .from(analyses)
    .innerJoin(attempts, eq(attempts.id, analyses.attemptId))
    .where(and(eq(attempts.userId, row.userId), eq(attempts.skill, skill), notInArray(attempts.id, parts.map((p) => p.id)), sql`coalesce((${analyses.result}->>'noSpeech')::boolean, false) = false`));
  const best = prev?.best != null && score > Number(prev.best);
  return { type: best ? 'best' : 'result', vars, route };
}

export const languageOf = (pref: PushLanguage | null, locale: string | null | undefined): PushLanguage => pref ?? (locale?.toLowerCase().startsWith('bn') ? 'banglish' : 'en');

/** Sends one claimed row to all of the student's devices and records the outcome. */
async function deliver(a: Account, row: Row): Promise<boolean> {
  const done = (status: Row['status'], extra: Partial<Row> = {}) => db.update(notificationLog).set({ status, ...extra }).where(eq(notificationLog.id, row.id));
  if (row.tries > MAX_TRIES) {
    await done('failed');
    return false;
  }
  const settings = await getSettings(row.userId);
  const devices = await activeDevices(row.userId);
  if (!devices.length) {
    await done('skipped');
    return false;
  }
  let push: Push;
  if (row.type === 'test') push = { id: row.id, type: 'test', title: row.title!, body: row.body!, route: row.route ?? '/settings' };
  else if (isNudge(row.type)) {
    // planned and written by push/planner.ts; re-checked now: switched off since, or a retry that ran into quiet hours / Jumu'ah, is dropped
    const data = (await db.query.userSettings.findFirst({ where: eq(userSettings.userId, row.userId), columns: { data: true } }))?.data;
    const tz = zoneIn(data);
    const ttl = nudgeTtl(row.type, mergeSettings({ ...data, timeZone: tz }), tz, new Date());
    if (!ttl) {
      await done('skipped');
      return false;
    }
    push = { id: row.id, type: row.type, title: row.title!, body: row.body!, route: row.route ?? '/', ttl };
  } else {
    const c = await resultContent(row);
    if (c === 'wait') {
      // not a try: back in a minute
      await db.update(notificationLog).set({ scheduledFor: sql`now() + interval '1 minute'`, tries: sql`${notificationLog.tries} - 1` }).where(eq(notificationLog.id, row.id));
      return false;
    }
    if (!c || !settings.notifyResults) {
      await done('skipped');
      return false;
    }
    const m = renderPush(c.type, languageOf(settings.notifyLanguage, devices[0]!.locale), c.vars);
    push = { id: row.id, type: c.type, title: m.title, body: m.body, route: c.route };
    await done('pending', { type: c.type, templateId: m.templateId, title: m.title, body: m.body, route: c.route });
  }
  const problems = checkPush(push);
  if (problems.length) {
    console.error('[push] copy rejected', row.id, problems);
    await done('rejected');
    return false;
  }
  let sent = false;
  let retryMs = 0;
  for (const d of devices) {
    const out = await sendOne(a, d, push, row.tries).catch((e) => (console.error('[push] send error', e), { retryMs: 60_000 * 2 ** (row.tries - 1) }));
    if (out === 'sent') sent = true;
    else if (out === 'dead') await db.update(notificationDevices).set({ disabledAt: new Date() }).where(eq(notificationDevices.token, d.token));
    else if (out !== 'error') retryMs = Math.max(retryMs, out.retryMs);
  }
  // ponytail: one device delivered = sent; a second device that hit a 5xx misses this one push (a per-device retry would need per-device rows)
  if (sent) await done('sent', { sentAt: new Date() });
  else if (retryMs && row.tries < MAX_TRIES) await db.update(notificationLog).set({ scheduledFor: new Date(Date.now() + retryMs) }).where(eq(notificationLog.id, row.id));
  else await done('failed');
  return sent;
}

let sweeping: Promise<number> | undefined;
/** Sends every due pending row. Rows are claimed with FOR UPDATE SKIP LOCKED and a 5-minute lease, so several server processes never send one
 *  row twice. Called every 30 s by index.ts; a no-op while push is off. */
export function pushSweep(): Promise<number> {
  return (sweeping ??= (async () => {
    const a = fcm();
    if (!a) return 0;
    let n = 0;
    try {
      const due = db.select({ id: notificationLog.id }).from(notificationLog).where(and(eq(notificationLog.status, 'pending'), lte(notificationLog.scheduledFor, sql`now()`))).orderBy(notificationLog.scheduledFor).limit(50).for('update', { skipLocked: true });
      const rows = await db.update(notificationLog).set({ tries: sql`${notificationLog.tries} + 1`, scheduledFor: LEASE }).where(inArray(notificationLog.id, due)).returning();
      for (const r of rows) if (await deliver(a, r).catch((e) => (console.error('[push] deliver failed', r.id, e), false))) n++;
    } catch (e) {
      console.error('[push] sweep failed', e);
    } finally {
      sweeping = undefined;
    }
    return n;
  })());
}

/** Settings → "Send me a test notification": queued and delivered now. 'rate_limited' = another test was queued in the last 30 s. */
export async function sendTestPush(userId: string): Promise<boolean | 'rate_limited'> {
  const a = fcm();
  if (!a) return false;
  const [settings, devices] = await Promise.all([getSettings(userId), activeDevices(userId)]);
  const m = renderPush('test', languageOf(settings.notifyLanguage, devices[0]?.locale), {});
  // check + insert under a per-student lock, so two taps at once can't both pass the 30 s limit
  const row = await db.transaction(async (tx) => {
    await tx.execute(sql`select pg_advisory_xact_lock(hashtext(${`push-test:${userId}`}))`);
    const [recent] = await tx.select({ id: notificationLog.id }).from(notificationLog)
      .where(and(eq(notificationLog.userId, userId), eq(notificationLog.type, 'test'), gt(notificationLog.createdAt, sql`now() - interval '30 seconds'`))).limit(1);
    if (recent) return null;
    const [r] = await tx
      .insert(notificationLog)
      .values({ userId, type: 'test', templateId: m.templateId, title: m.title, body: m.body, route: '/settings', dedupeKey: `test:${crypto.randomUUID()}`, tries: 1, scheduledFor: LEASE })
      .returning();
    return r!;
  });
  return row ? deliver(a, row) : 'rate_limited';
}

/** The student practised (streaks.ts recordActivity): nudges sent, and holdout rows logged, in the 2 h before `at` count as acted on (§5.7;
 *  the bandit's win). Never throws. */
export async function markActed(userId: string, at = new Date()): Promise<void> {
  const from = new Date(at.getTime() - 2 * 3_600_000);
  await db
    .update(notificationLog)
    .set({ actedAt: at })
    .where(and(eq(notificationLog.userId, userId), isNull(notificationLog.actedAt), inArray(notificationLog.type, [...NUDGE_TYPES]),
      or(and(eq(notificationLog.status, 'sent'), gte(notificationLog.sentAt, from), lte(notificationLog.sentAt, at)),
        and(eq(notificationLog.holdout, true), gte(notificationLog.scheduledFor, from), lte(notificationLog.scheduledFor, at)))))
    .catch((e) => console.error('[push] markActed failed', userId, e));
}
