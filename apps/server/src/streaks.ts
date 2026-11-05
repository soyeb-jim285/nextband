// Streaks (docs/engagement/notifications-and-streaks.md §3). The streak is a pure replay of the student's practice days up to today:
// freezes, breaks and repair windows all follow from those days, so a read settles missed days lazily (no nightly sweep) and the
// `streaks` row is only a cache of the latest replay. ponytail: replays the whole history each time (one row per active day, a few hundred at most).
import { asc, eq, sql } from 'drizzle-orm';
import { localDay } from './coach/report';
import { db } from './db/client';
import { activityDays, streaks, userSettings } from './db/schema';
import { markActed } from './push';
import { isTimeZone } from './settings';

const DAY = 864e5;
export const MILESTONES = [3, 7, 14, 30, 50, 100, 200, 365];
const MAX_FREEZES = 2;

export type Activity = { day: string; tests: number; drills: number; minutes: number };
export const isPracticeDay = (a: Omit<Activity, 'day'>) => a.tests >= 1 || a.drills >= 5 || a.minutes >= 10;
/** Heatmap level: 0 none, 1 drills/minutes only, 2 a test, 3 a test plus drills (or 3+ tests). */
export const level = (a: Omit<Activity, 'day'> | undefined) =>
  !a || !isPracticeDay(a) ? 0 : a.tests >= 3 || (a.tests >= 1 && a.drills >= 5) ? 3 : a.tests >= 1 ? 2 : 1;
export const addDays = (d: string, n: number) => new Date(Date.parse(`${d}T00:00:00Z`) + n * DAY).toISOString().slice(0, 10);

export type Replay = {
  current: number;
  longest: number;
  lastDay: string | null;
  freezes: number;
  frozenDays: string[];
  /** Broken with no freeze: practise on both days `start` and `start`+1 to get `target` back (plus those 2 days). */
  repair: { target: number; missed: string; start: string; daysDone: number } | null;
};

/** Replays practice days (YYYY-MM-DD, local) up to `today`. Today not yet practised is pending, never missed. */
export function replay(practiceDays: Iterable<string>, today: string): Replay {
  const done = new Set(practiceDays);
  const first = [...done].sort()[0];
  const s: Replay = { current: 0, longest: 0, lastDay: null, freezes: 0, frozenDays: [], repair: null };
  if (!first) return s;
  for (let d = first; d <= today; d = addDays(d, 1)) {
    if (done.has(d)) {
      if (s.repair && ++s.repair.daysDone === 2) {
        s.current = s.repair.target + 2;
        s.frozenDays.push(s.repair.missed); // the repaired day shows like a frozen one
        s.repair = null;
      } else s.current++;
      if (s.current % 7 === 0) s.freezes = Math.min(MAX_FREEZES, s.freezes + 1); // ponytail: a repair that jumps past a multiple of 7 earns nothing
      s.longest = Math.max(s.longest, s.current);
      s.lastDay = d;
    } else if (d < today) {
      s.repair = null; // a missed day inside the 48 h window (its 2 days) ends the repair offer
      if (!s.current) continue;
      if (s.freezes) {
        s.freezes--;
        s.frozenDays.push(d);
      } else {
        if (s.current >= 2) s.repair = { target: s.current, missed: d, start: addDays(d, 1), daysDone: 0 }; // ponytail: a 1-day streak is not worth a repair offer
        s.current = 0;
      }
    }
  }
  return s;
}

/** The instant local midnight starts `day` in `timeZone`. */
export function startOfDay(day: string, timeZone: string): Date {
  const fmt = new Intl.DateTimeFormat('en-US', { timeZone, hourCycle: 'h23', year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit' });
  const offset = (t: number) => {
    const p = Object.fromEntries(fmt.formatToParts(t).map((x) => [x.type, x.value]));
    return Date.UTC(+p.year!, +p.month! - 1, +p.day!, +p.hour!, +p.minute!, +p.second!) - t;
  };
  const g = Date.parse(`${day}T00:00:00Z`);
  return new Date(g - offset(g - offset(g))); // second pass settles DST edges
}

type Tx = Pick<typeof db, 'select' | 'insert' | 'query'>;

/** The student's zone for streak days: user_settings timeZone, Asia/Dhaka when unset or unknown. */
async function zoneOf(userId: string, x: Tx = db): Promise<string> {
  const row = await x.query.userSettings.findFirst({ where: eq(userSettings.userId, userId), columns: { data: true } });
  const tz = row?.data?.timeZone;
  return typeof tz === 'string' && isTimeZone(tz) ? tz : 'Asia/Dhaka';
}

/** Replays the user's days up to `now` and caches the result in `streaks`. */
async function settle(x: Tx, userId: string, timeZone: string, now: Date) {
  const rows: Activity[] = await x.select({ day: activityDays.day, tests: activityDays.tests, drills: activityDays.drills, minutes: activityDays.minutes }).from(activityDays).where(eq(activityDays.userId, userId)).orderBy(asc(activityDays.day));
  const today = localDay(now, timeZone);
  const s = replay(rows.filter(isPracticeDay).map((r) => r.day), today);
  const repairUntil = s.repair ? startOfDay(addDays(s.repair.start, 2), timeZone) : null;
  const v = { current: s.current, longest: s.longest, lastDay: s.lastDay, freezes: s.freezes, frozenDays: s.frozenDays, repairUntil };
  await x.insert(streaks).values({ userId, ...v }).onConflictDoUpdate({ target: streaks.userId, set: { ...v, updatedAt: new Date() } });
  return { s, rows, today, repairUntil };
}

type Add = Partial<Record<'tests' | 'drills' | 'minutes', number>>;

/** Adds practice to the user's local day and updates the streak in the same transaction. Never throws: a streak error must not fail a submit. */
export async function recordActivity(userId: string, add: Add, at = new Date()): Promise<void> {
  const v = Object.fromEntries(Object.entries(add).filter(([, n]) => n && n > 0)) as Add;
  if (!Object.keys(v).length) return;
  try {
    await db.transaction(async (tx) => {
      const tz = await zoneOf(userId, tx);
      const set = Object.fromEntries(Object.entries(v).map(([k, n]) => [k, sql`${activityDays[k as keyof Add]} + ${n}`]));
      await tx.insert(activityDays).values({ userId, day: localDay(at, tz), ...v }).onConflictDoUpdate({ target: [activityDays.userId, activityDays.day], set });
      await settle(tx, userId, tz, at);
      await tx.update(streaks).set({ practisedAt: sql`greatest(${streaks.practisedAt}, ${at.toISOString()}::timestamptz)` }).where(eq(streaks.userId, userId));
    });
    await markActed(userId, at);
  } catch (e) {
    console.error('recordActivity', userId, (e as Error).message);
  }
}

/** Guest → account (link.ts, inside its transaction): the guest's days add onto the account's. */
export async function mergeDays(x: Pick<typeof db, 'execute' | 'delete'>, anonId: string, newId: string) {
  await x.execute(sql`insert into activity_days (user_id, day, tests, drills, minutes) select ${newId}, day, tests, drills, minutes from activity_days where user_id = ${anonId}
    on conflict (user_id, day) do update set tests = activity_days.tests + excluded.tests, drills = activity_days.drills + excluded.drills, minutes = activity_days.minutes + excluded.minutes`);
  await x.delete(activityDays).where(eq(activityDays.userId, anonId));
}

export type WeekState = 'done' | 'frozen' | 'missed' | 'today' | 'future';

/** Everything GET /api/streak returns; progress.ts uses `current`. */
export async function getStreak(userId: string, now = new Date()) {
  const tz = await zoneOf(userId);
  const { s, rows, today, repairUntil } = await settle(db, userId, tz, now);
  const byDay = new Map(rows.map((r) => [r.day, r]));
  const practised = (d: string) => { const r = byDay.get(d); return !!r && isPracticeDay(r); };
  const frozen = new Set(s.frozenDays);
  const monday = addDays(today, -((new Date(`${today}T00:00:00Z`).getUTCDay() + 6) % 7));
  const week = Array.from({ length: 7 }, (_, i) => {
    const day = addDays(monday, i);
    const state: WeekState = day > today ? 'future' : practised(day) ? 'done' : day === today ? 'today' : frozen.has(day) ? 'frozen' : 'missed';
    return { day, state };
  });
  const lvl = (day: string) => level(byDay.get(day));
  const month: { day: string; level: number }[] = [];
  for (let day = `${today.slice(0, 8)}01`; day.slice(0, 7) === today.slice(0, 7); day = addDays(day, 1)) month.push({ day, level: lvl(day) });
  const days = Array.from({ length: 182 }, (_, i) => {
    const day = addDays(today, i - 181);
    return { day, level: lvl(day), frozen: frozen.has(day) };
  });
  const doneToday = practised(today);
  return {
    date: today,
    current: s.current,
    longest: s.longest,
    freezes: s.freezes,
    today: doneToday ? ('done' as const) : ('pending' as const),
    week,
    month,
    days,
    practicedLast35: days.slice(-35).filter((d) => d.level > 0).length,
    nextMilestone: MILESTONES.find((m) => m > s.current) ?? null,
    repair: s.repair && repairUntil ? { target: s.repair.target, until: repairUntil.toISOString(), daysDone: s.repair.daysDone } : null,
    // the count only grows on a practice day, so a milestone count with today done was reached today
    ...(doneToday && MILESTONES.includes(s.current) && { justReached: s.current }),
  };
}

/** Recomputes the cached row (after a guest merge). Never throws. */
export async function refreshStreak(userId: string) {
  try {
    await getStreak(userId);
  } catch (e) {
    console.error('refreshStreak', userId, (e as Error).message);
  }
}
