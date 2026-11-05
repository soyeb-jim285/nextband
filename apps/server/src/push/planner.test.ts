import { afterEach, beforeEach, expect, it, vi } from 'vitest';
// helpers first: it loads the app, which installs zod .openapi() before settings.ts is evaluated
import { fakeFetch, guestUser, json, req, seedPrompt } from '../test/helpers';
import { generateKeyPairSync } from 'node:crypto';
import { and, eq } from 'drizzle-orm';
import { NUDGE_TEMPLATES } from '@ielts/core';
import { db } from '../db/client';
import { activityDays, analyses, attempts, cards, lrTests, mistakes, mockExams, notificationDevices, notificationLog, user, userSettings } from '../db/schema';
import { setFetch } from '../ai/openrouter';
import { pushSweep, setFcmAccount } from '../push';
import { getSettings } from '../settings';
import { getStreak, recordActivity } from '../streaks';
import { candidates, nudgeSweep, planUser, remindAtFor, templateStats } from './planner';
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
  expect(row).toMatchObject({ type: 'streak', status: 'pending', holdout: false, route: '/streak', dedupeKey: `nudge:${id}:${THU}`, templateId: expect.stringMatching(/^saver-en-/) });
  expect(`${row!.title} ${row!.body}`).not.toContain('{');

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
  // the student opens the app (a foreground PUT): level 0 again, so the due drills come back (it's past Jumuah)
  await db.update(notificationDevices).set({ appOpenedAt: at('2026-10-31T09:00') }).where(eq(notificationDevices.userId, id));
  expect(await nudgeSweep(at('2026-10-31T19:30'))).toBe(1);
  expect((await logs(id)).at(-1)).toMatchObject({ type: 'drills', route: '/improve/drill' });
});

it('back-off: the goodbye holds after its rows age out of the 60 days; 61 days on, no opens, only the exam countdown', async () => {
  const id = await student(false, { examDate: '2026-11-05' }); // 7 days after THU
  await db.insert(cards).values(Array.from({ length: 6 }, (_, i) => ({ userId: id, front: `f${i}`, back: 'b', source: 'mistake', due: new Date('2026-10-01T00:00:00Z') })));
  const ago = (d: number) => new Date(at(`${THU}T19:30`).getTime() - d * DAY);
  for (let d = 70; d <= 77; d++) await sent(id, 'drills', ago(d), { createdAt: ago(d) });
  await sent(id, 'goodbye', ago(62), { createdAt: ago(62) });
  expect(await nudgeSweep(at('2026-10-28T19:30'))).toBe(0); // drills due, but no second goodbye and no drills
  expect((await logs(id)).length).toBe(9);
  expect(await nudgeSweep(at(`${THU}T19:30`))).toBe(1);
  expect((await logs(id)).at(-1)).toMatchObject({ type: 'exam' });
});

it('back-off: an FCM wake-up after each push (last_seen_at moves, app never opened) does not reset it; only a foreground PUT does', async () => {
  const id = await student();
  for (let d = 9; d <= 16; d++) {
    const when = new Date(at(`${THU}T19:30`).getTime() - d * DAY);
    await sent(id, 'drills', when);
    // the push woke the app process: AppGraph re-registers the device in the background, seconds later
    await db.update(notificationDevices).set({ lastSeenAt: new Date(when.getTime() + 5000) }).where(eq(notificationDevices.userId, id));
  }
  expect(await nudgeSweep(at(`${THU}T19:30`))).toBe(1);
  expect((await logs(id)).at(-1)).toMatchObject({ type: 'goodbye' });

  // the route: a background PUT leaves app_opened_at alone, a foreground one sets it
  const g = await guestUser();
  const put = (extra: object) => req('/api/push/token', { method: 'PUT', headers: g.headers, body: { token: `fid-${g.user.id}`, kind: 'fid', platform: 'android', ...extra } });
  const opened = async () => (await db.select().from(notificationDevices).where(eq(notificationDevices.userId, g.user.id)))[0]!.appOpenedAt;
  expect((await put({})).status).toBe(200);
  expect(await opened()).toBeNull();
  expect((await put({ foreground: true })).status).toBe(200);
  const first = await opened();
  expect(first).toBeInstanceOf(Date);
  expect((await put({})).status).toBe(200);
  expect(await opened()).toEqual(first);
});

it('back-off: practising after a push resets it, even later the same day', async () => {
  const id = await student();
  // 8 ignored pushes on the 20th (written straight into the log), then practice at 22:00 that day, outside their 2 h windows
  for (let h = 10; h <= 17; h++) await sent(id, 'drills', at(`2026-10-20T${h}:00`));
  await recordActivity(id, { drills: 5 }, at('2026-10-20T22:00'));
  const settings = { ...(await getSettings(id)), timeZone: 'Asia/Dhaka' };
  await db.insert(cards).values(Array.from({ length: 5 }, (_, i) => ({ userId: id, front: `f${i}`, back: 'b', source: 'mistake', due: new Date('2026-10-01T00:00:00Z') })));
  expect((await candidates(id, settings, 'Asia/Dhaka', at(`${THU}T19:30`))).map((c) => c.type)).toContain('drills');
  expect(await nudgeSweep(at(`${THU}T19:30`))).toBe(1);
  expect((await logs(id)).at(-1)).toMatchObject({ type: 'drills' }); // level 0, not the goodbye
});

it('back-off at level 3 with practice reminders off: no goodbye, the exam countdown still goes', async () => {
  const id = await student(false, { examDate: '2026-11-05', notifyPractice: false });
  for (let d = 9; d <= 16; d++) await sent(id, 'drills', new Date(at(`${THU}T19:30`).getTime() - d * DAY));
  expect(await nudgeSweep(at(`${THU}T19:30`))).toBe(1);
  expect((await logs(id)).at(-1)).toMatchObject({ type: 'exam' });
});

it('the holdout backs off like everyone else: 8 holdout rows since it last opened the app log a holdout goodbye', async () => {
  const h = await student(true, { examDate: '2026-12-31' });
  for (let d = 9; d <= 16; d++) await sent(h, 'drills', new Date(at(`${THU}T19:30`).getTime() - d * DAY), { status: 'skipped', holdout: true, sentAt: null });
  await practised(h, '2026-10-27', '2026-10-28'); // activity days alone (no practised_at) reset nothing
  expect(await nudgeSweep(at(`${THU}T19:30`))).toBe(0);
  expect((await logs(h)).at(-1)).toMatchObject({ type: 'goodbye', holdout: true, status: 'skipped' });
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
  await db.insert(cards).values(Array.from({ length: 5 }, (_, i) => ({ userId: id, front: `f${i}`, back: 'b', source: 'mistake', category: 'grammar.article', due: new Date('2026-10-01T00:00:00Z') })));
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
  expect(c.find((x) => x.type === 'pattern')).toMatchObject({ vars: { pattern: 'Article mistakes', skill: 'Writing', hits: '3', of: '4' }, route: '/improve/drill?pattern=grammar.article' });
  expect(c.find((x) => x.type === 'cold')).toMatchObject({ vars: { skill: 'Speaking', idle: '10', band: '6.0' }, route: '/speaking' });
  expect(c.find((x) => x.type === 'exam')!.vars).toEqual({ days: '14', band: '6.5', weakest: 'Speaking' });
  expect(c.find((x) => x.type === 'winback')!.vars).toMatchObject({ idle: '3', band: '6.5' });
  expect(c.find((x) => x.type === 'drills')!.vars).toEqual({ due: '5' });
  expect(c.some((x) => x.noDrill)).toBe(false); // 5 cards: drill copy is fine
  // P1 pattern and exam beat the rest; with both fresh the first listed wins
  expect(await nudgeSweep(now)).toBe(1);
  expect(['pattern', 'exam']).toContain((await logs(id))[0]!.type);
});

it('milestone near replaces the streak saver two practice days before a milestone', async () => {
  const id = await student();
  await practised(id, '2026-10-28'); // a 1-day streak, today not yet: today and tomorrow make day 3
  const settings = { ...(await getSettings(id)), timeZone: 'Asia/Dhaka' };
  // no cards yet: only copy that doesn't send the student to drills
  expect(await candidates(id, settings, 'Asia/Dhaka', at(`${THU}T19:30`))).toEqual([{ type: 'milestone', vars: { milestone: '3' }, route: '/streak', noDrill: true }]);
  // 5 due cards make #5 a candidate too; the merged #3/#9 keeps the streak saver's P1 and wins
  await db.insert(cards).values(Array.from({ length: 5 }, (_, i) => ({ userId: id, front: `f${i}`, back: 'b', source: 'mistake', due: new Date('2026-10-01T00:00:00Z') })));
  expect(await nudgeSweep(at(`${THU}T19:30`))).toBe(1);
  const [row] = await logs(id);
  expect(row).toMatchObject({ type: 'milestone', templateId: expect.stringMatching(/^milestone-en-/) });
  expect(`${row!.title} ${row!.body}`).not.toContain('{');
});

it('pattern alert: "of" is how many marked tests there are, at most 4; no cards for it, no alert; under 5, no drill-promising copy', async () => {
  const id = await student();
  const now = at(`${THU}T19:30`);
  const w = await seedPrompt({ skill: 'writing', part: 2, type: 'opinion' });
  for (let i = 0; i < 3; i++) {
    const [a] = await db.insert(attempts).values({ userId: id, promptId: w.id, skill: 'writing', part: 2, text: 'x', status: 'done', createdAt: new Date(now.getTime() - (i + 1) * 3_600_000) }).returning();
    await db.insert(analyses).values({ attemptId: a!.id, result: { errors: [] }, overall: 6.5, criteria: {}, models: {} });
    await db.insert(mistakes).values({ userId: id, attemptId: a!.id, errorId: 'e', category: 'task.relevance', original: 'a', correction: 'b', explanation: 'x' });
  }
  const settings = { ...(await getSettings(id)), timeZone: 'Asia/Dhaka' };
  const pattern = async () => (await candidates(id, settings, 'Asia/Dhaka', now)).find((x) => x.type === 'pattern');
  // 5 cards, but none for this mistake (one is suspended)
  await db.insert(cards).values(Array.from({ length: 5 }, (_, i) => ({ userId: id, front: `f${i}`, back: 'b', source: 'mistake', category: 'grammar.article' })));
  await db.insert(cards).values({ userId: id, front: 'skipped', back: 'b', source: 'mistake', category: 'task.relevance', suspended: true });
  expect(await pattern()).toBeUndefined();
  await db.insert(cards).values(Array.from({ length: 2 }, (_, i) => ({ userId: id, front: `r${i}`, back: 'b', source: 'mistake', category: 'task.relevance' })));
  expect(await pattern()).toMatchObject({ vars: { pattern: 'Going off topic', skill: 'Writing', hits: '3', of: '3' }, noDrill: true });
  await db.insert(cards).values(Array.from({ length: 3 }, (_, i) => ({ userId: id, front: `s${i}`, back: 'b', source: 'mistake', category: 'task.relevance' })));
  expect((await pattern())!.noDrill).toBeUndefined();
});

it('mock ready: only a mock with some sections done and some left, started over 24 h ago', async () => {
  const id = await student();
  const now = at(`${THU}T19:30`);
  const test = async (skill: 'listening' | 'reading') =>
    (await db.insert(lrTests).values({ slug: `push-${skill}-${id}`, skill, variant: 'academic', source: 'generated', ref: 'X', title: 'x', data: { sections: [] } as never, restricted: false }).returning())[0]!.id;
  const [l, r] = [await test('listening'), await test('reading')];
  const [m] = await db.insert(mockExams).values({ userId: id, variant: 'academic', source: 'generated', listeningTestId: l, readingTestId: r, writingPromptIds: [], writingSessionId: 'ws',
    startedAt: new Date(now.getTime() - 2 * DAY), expiresAt: new Date(now.getTime() + 5 * DAY) }).returning();
  const settings = { ...(await getSettings(id)), timeZone: 'Asia/Dhaka' };
  const mock = async () => (await candidates(id, settings, 'Asia/Dhaka', now)).find((x) => x.type === 'mock');
  expect(await mock()).toBeUndefined(); // nothing begun: "your answers so far are saved" would be false
  await db.update(mockExams).set({ listeningAttemptId: 'la' }).where(eq(mockExams.id, m!.id));
  expect(await mock()).toMatchObject({ vars: { parts: 'Reading, Writing and Speaking' }, route: `/mock/${m!.id}` });
});

it('the same student never gets the same streak-saver copy on consecutive days, even when the bandit favours it', async () => {
  const id = await student();
  await practised(id, '2026-10-27', '2026-10-28');
  const settings = { ...(await getSettings(id)), timeZone: 'Asia/Dhaka' };
  const stats = new Map<string, { sends: number; wins: number }>();
  expect(await planUser(id, settings, 'Asia/Dhaka', at(`${THU}T19:20`), stats)).toBe('queued');
  const first = (await logs(id))[0]!.templateId!;
  for (const t of NUDGE_TEMPLATES['streak-saver'].en) stats.set(t.id, { sends: 1000, wins: t.id === first ? 990 : 10 }); // a clear winner
  await practised(id, THU);
  expect(await planUser(id, settings, 'Asia/Dhaka', at('2026-10-30T19:20'), stats)).toBe('queued');
  const [, second] = await logs(id);
  expect(second).toMatchObject({ type: 'streak', templateId: expect.stringMatching(/^saver-en-/) });
  expect(second!.templateId).not.toBe(first);
});
it('a saved freeze: the streak saver goes without FOMO copy', async () => {
  const id = await student();
  await practised(id, ...Array.from({ length: 8 }, (_, i) => `2026-10-${String(20 + i).padStart(2, '0')}`).concat('2026-10-28')); // 9 days: 1 freeze earned
  const settings = { ...(await getSettings(id)), timeZone: 'Asia/Dhaka' };
  const [c] = await candidates(id, settings, 'Asia/Dhaka', at(`${THU}T19:30`));
  expect(c).toMatchObject({ type: 'streak', noFomo: true });
});

it('remindAt (the app\'s local streak reminder): only with a streak at risk, its switch on, outside the holdout, back-off and caps', async () => {
  const now = at(`${THU}T10:00`);
  const remind = async (id: string) => (await getStreak(id, now), remindAtFor(id, now));
  const ok = await student();
  await practised(ok, '2026-10-27', '2026-10-28');
  expect(await remind(ok)).toBe('19:30');
  const none = await student(); // no streak
  expect(await remind(none)).toBeNull();
  const done = await student();
  await practised(done, '2026-10-28', THU); // practised today: nothing at risk
  expect(await remind(done)).toBeNull();
  const off = await student(false, { notifyStreak: false });
  const hold = await student(true);
  const capped = await student();
  const backedOff = await student();
  for (const id of [off, hold, capped, backedOff]) await practised(id, '2026-10-27', '2026-10-28');
  await sent(capped, 'drills', at(`${THU}T09:00`)); // today's nudge already went
  for (let d = 9; d <= 13; d++) await sent(backedOff, 'drills', new Date(at(`${THU}T19:30`).getTime() - d * DAY)); // level 2, caps clear
  for (const id of [off, hold, capped, backedOff]) expect(await remind(id), id).toBeNull();
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

it('GET /api/streak returns remindAt (null: a new guest has no streak at risk)', async () => {
  const g = await guestUser();
  const r = await req('/api/streak', { headers: g.headers });
  expect(r.status).toBe(200);
  expect(await r.json()).toMatchObject({ remindAt: null });
});
