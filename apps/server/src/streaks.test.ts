import { guestUser, req, testUser } from './test/helpers'; // first: loads the app before the modules that import it back
import { eq } from 'drizzle-orm';
import { describe, expect, it } from 'vitest';
import { db } from './db/client';
import { activityDays, cards, streaks, userSettings } from './db/schema';
import { linkGuest } from './link';
import { addDays, getStreak, isPracticeDay, level, recordActivity, replay, startOfDay } from './streaks';

const DAY = 864e5;
const run = (from: string, n: number) => Array.from({ length: n }, (_, i) => addDays(from, i));
const body = async (r: Response) => (await r.json()) as any;

describe('replay', () => {
  it('counts consecutive days ending today or yesterday; today not done yet is pending', () => {
    expect(replay(run('2026-11-01', 3), '2026-11-03')).toMatchObject({ current: 3, longest: 3, lastDay: '2026-11-03' });
    expect(replay(run('2026-11-01', 3), '2026-11-04').current).toBe(3);
    expect(replay([], '2026-11-04')).toMatchObject({ current: 0, longest: 0, repair: null });
  });

  it('earns a freeze per 7 days in a row, holds at most 2, and a missed day uses one', () => {
    const s = replay([...run('2026-11-01', 7), '2026-11-09'], '2026-11-10');
    expect(s).toMatchObject({ current: 8, freezes: 0, frozenDays: ['2026-11-08'] });
    expect(replay(run('2026-10-01', 7), '2026-10-07').freezes).toBe(1);
    expect(replay(run('2026-10-01', 14), '2026-10-14').freezes).toBe(2);
    expect(replay(run('2026-10-01', 28), '2026-10-28').freezes).toBe(2);
  });

  it('settles several missed days lazily: freezes first, then the break, then the repair window closes', () => {
    const days = run('2026-10-17', 14); // ends 10-30, 2 freezes
    expect(replay(days, '2026-11-02')).toMatchObject({ current: 14, freezes: 0, frozenDays: ['2026-10-31', '2026-11-01'], repair: null });
    expect(replay(days, '2026-11-03')).toMatchObject({ current: 0, longest: 14, repair: { target: 14, missed: '2026-11-02', start: '2026-11-03', daysDone: 0 } });
    expect(replay(days, '2026-11-04')).toMatchObject({ current: 0, longest: 14, repair: null }); // 11-03 missed too: two days in the window are impossible
  });

  it('repair: practising on both days of the window brings the streak back', () => {
    const before = run('2026-10-28', 5); // 10-28..11-01, missed 11-02
    expect(replay([...before, '2026-11-03'], '2026-11-03')).toMatchObject({ current: 1, repair: { target: 5, daysDone: 1 } });
    expect(replay([...before, '2026-11-03', '2026-11-04'], '2026-11-04')).toMatchObject({ current: 7, longest: 7, repair: null, frozenDays: ['2026-11-02'] });
  });

  it('repair fails when the second day is missed; the longest stays', () => {
    expect(replay([...run('2026-10-28', 5), '2026-11-03'], '2026-11-05')).toMatchObject({ current: 0, longest: 5, repair: null });
  });

  it('a practice day is a test, 5 drill cards or 10 minutes', () => {
    expect(isPracticeDay({ tests: 1, drills: 0, minutes: 0 })).toBe(true);
    expect(isPracticeDay({ tests: 0, drills: 4, minutes: 9 })).toBe(false);
    expect(isPracticeDay({ tests: 0, drills: 5, minutes: 0 })).toBe(true);
    expect(isPracticeDay({ tests: 0, drills: 0, minutes: 10 })).toBe(true);
  });

  it('heatmap levels: drills only 1, a test 2, a test plus drills or 3 tests 3', () => {
    expect(level(undefined)).toBe(0);
    expect(level({ tests: 0, drills: 4, minutes: 0 })).toBe(0);
    expect(level({ tests: 0, drills: 5, minutes: 0 })).toBe(1);
    expect(level({ tests: 0, drills: 0, minutes: 15 })).toBe(1);
    expect(level({ tests: 1, drills: 4, minutes: 0 })).toBe(2);
    expect(level({ tests: 1, drills: 5, minutes: 0 })).toBe(3);
    expect(level({ tests: 3, drills: 0, minutes: 0 })).toBe(3);
  });

  it('local midnight in a zone', () => {
    expect(startOfDay('2026-11-05', 'Asia/Dhaka').toISOString()).toBe('2026-11-04T18:00:00.000Z');
    expect(startOfDay('2026-03-29', 'Europe/London').toISOString()).toBe('2026-03-29T00:00:00.000Z');
    expect(startOfDay('2026-07-01', 'Europe/London').toISOString()).toBe('2026-06-30T23:00:00.000Z');
  });
});

describe('recordActivity and GET /api/streak', () => {
  it('counts a 01:00 Dhaka session on that Dhaka day (default zone); a UTC student gets the UTC day', async () => {
    const { user } = await testUser();
    const at = new Date('2026-11-04T19:30:00Z'); // 01:30 on 11-05 in Dhaka
    await recordActivity(user.id, { tests: 1 }, at);
    expect((await db.select().from(activityDays).where(eq(activityDays.userId, user.id)))[0]?.day).toBe('2026-11-05');
    const s = await getStreak(user.id, new Date('2026-11-05T10:00:00Z'));
    expect(s).toMatchObject({ date: '2026-11-05', current: 1, today: 'done' }); // the local day, not UTC 11-04
    expect(s.week.map((w) => w.state)).toEqual(['missed', 'missed', 'missed', 'done', 'future', 'future', 'future']); // Mon 11-02 .. Sun 11-08
    expect(s.month).toHaveLength(30);
    expect(s.month[4]).toEqual({ day: '2026-11-05', level: 2 });
    expect(s.days).toHaveLength(182);
    expect(s.days.at(-1)).toEqual({ day: '2026-11-05', level: 2, frozen: false });
    expect(s.days[0]!.day).toBe('2026-05-08');
    expect(s.practicedLast35).toBe(1);

    const { user: u2 } = await testUser();
    await db.insert(userSettings).values({ userId: u2.id, data: { timeZone: 'UTC' } });
    await recordActivity(u2.id, { tests: 1 }, at);
    expect((await db.select().from(activityDays).where(eq(activityDays.userId, u2.id)))[0]?.day).toBe('2026-11-04');
  });

  it('milestones, freezes and the repair window come back from the API shape', async () => {
    const { user } = await testUser();
    const now = new Date('2026-11-05T08:00:00Z');
    for (let d = 2; d >= 0; d--) await recordActivity(user.id, { tests: 1 }, new Date(now.getTime() - d * DAY));
    expect(await getStreak(user.id, now)).toMatchObject({ current: 3, justReached: 3, nextMilestone: 7, repair: null });
    const later = await getStreak(user.id, new Date(now.getTime() + 2 * DAY)); // missed 11-06, no freeze
    expect(later).toMatchObject({ current: 0, longest: 3, today: 'pending', repair: { target: 3, until: '2026-11-08T18:00:00.000Z', daysDone: 0 } });
    expect(later.justReached).toBeUndefined();
    const row = await db.query.streaks.findFirst({ where: eq(streaks.userId, user.id) });
    expect(row).toMatchObject({ current: 0, longest: 3, lastDay: '2026-11-05' });
    expect(row?.repairUntil?.toISOString()).toBe('2026-11-08T18:00:00.000Z');
  });

  it('5 graded drill cards make a practice day, 4 do not', async () => {
    const { headers, user } = await testUser();
    const ids = (await db.insert(cards).values(Array.from({ length: 5 }, (_, i) => ({ userId: user.id, front: `f${i}`, back: 'b', source: 'vocab' }))).returning()).map((c) => c.id);
    for (const id of ids.slice(0, 4)) expect((await req(`/api/cards/${id}/review`, { headers, body: { grade: 4 } })).status).toBe(200);
    expect(await body(await req('/api/streak', { headers }))).toMatchObject({ current: 0, today: 'pending' });
    await req(`/api/cards/${ids[4]}/review`, { headers, body: { grade: 4 } });
    expect(await body(await req('/api/streak', { headers }))).toMatchObject({ current: 1, today: 'done' });
  });

  it('progress reports the same streak as /api/streak', async () => {
    const { headers, user } = await testUser();
    for (const d of [0, 1, 3]) await recordActivity(user.id, { minutes: 12 }, new Date(Date.now() - d * DAY));
    const s = await body(await req('/api/streak', { headers }));
    expect(s.current).toBe(2);
    expect(s.practicedLast35).toBe(3);
    expect(s.days.at(-4)).toMatchObject({ level: 1, frozen: false });
    expect((await body(await req('/api/progress', { headers }))).streak).toBe(s.current);
  });

  it('guests get a streak, and it moves onto the account they link', async () => {
    const g = await guestUser();
    await recordActivity(g.user.id, { tests: 1 }, new Date(Date.now() - DAY));
    await recordActivity(g.user.id, { drills: 3 });
    expect(await body(await req('/api/streak', { headers: g.headers }))).toMatchObject({ current: 1, today: 'pending' });
    const { user } = await testUser();
    await recordActivity(user.id, { drills: 2 }); // with the guest's 3: today counts
    await linkGuest(g.user.id, user.id);
    expect(await db.select().from(activityDays).where(eq(activityDays.userId, g.user.id))).toEqual([]);
    expect(await getStreak(user.id)).toMatchObject({ current: 2, today: 'done' });
  });
});
