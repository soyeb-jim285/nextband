// Round C nudge rules (docs/engagement/notifications-and-streaks.md §2, §4, §5.2–§5.5), all pure so tests run them on fixed clocks.
// The planner (planner.ts) feeds them from the database; push.ts uses the quiet-hours check again at delivery.
import { createHash } from 'node:crypto';
import type { PushChannel } from '@ielts/core';
import { isTimeZone, type Settings } from '../settings';

/** The P1/P2 types (§4) the server sends. #4 last call is local only (the app), #11 weekly recap is round D (templates.ts NOT_WIRED), #9 milestone near has its own copy. */
export const NUDGE_TYPES = ['streak', 'milestone', 'drills', 'pattern', 'exam', 'cold', 'winback', 'goodbye', 'mock'] as const;
export type NudgeType = (typeof NUDGE_TYPES)[number];
export const isNudge = (t: string): t is NudgeType => (NUDGE_TYPES as readonly string[]).includes(t);

/** Android channel (§6) and the Settings switch (settings.ts) of each type. */
export const NUDGE_CHANNEL: Record<NudgeType, PushChannel> = {
  streak: 'streak', milestone: 'streak', drills: 'practice', pattern: 'practice', exam: 'exam', cold: 'practice', winback: 'practice', goodbye: 'practice', mock: 'practice',
};
export const NUDGE_SWITCH: Record<NudgeType, keyof Settings> = {
  streak: 'notifyStreak', milestone: 'notifyStreak', drills: 'notifyPractice', pattern: 'notifyPractice', exam: 'notifyExam', cold: 'notifyPractice', winback: 'notifyPractice', goodbye: 'notifyPractice', mock: 'notifyPractice',
};
/** §4 priority: P1 = timely and valuable (2), P2 = nudge (1). #9 milestone near only runs merged into #3 (planner), so it keeps #3's P1. */
export const PRIORITY: Record<NudgeType, number> = { streak: 2, milestone: 2, drills: 1, pattern: 2, exam: 2, cold: 1, winback: 1, goodbye: 1, mock: 1 };
/** Days before a type is fully "fresh" again after it was sent (§5.3 freshness). */
const COOLDOWN: Record<NudgeType, number> = { streak: 1, milestone: 1, drills: 2, pattern: 7, exam: 1, cold: 7, winback: 3, goodbye: 1, mock: 3 };

// ---------- Local clock ----------
export const DAY_MIN = 1440;
export const hhmm = (s: string) => +s.slice(0, 2) * 60 + +s.slice(3, 5);
export const fmtMin = (m: number) => `${String(Math.floor(m / 60)).padStart(2, '0')}:${String(m % 60).padStart(2, '0')}`;

/** The local day (YYYY-MM-DD), minute of the day and weekday (0 = Sunday, 5 = Friday) of `at` in `timeZone`. */
export function localClock(at: Date, timeZone: string) {
  const p = Object.fromEntries(
    new Intl.DateTimeFormat('en-US', { timeZone, hourCycle: 'h23', year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', weekday: 'short' })
      .formatToParts(at).map((x) => [x.type, x.value]),
  );
  return { day: `${p.year}-${p.month}-${p.day}`, minute: +p.hour! * 60 + +p.minute!, weekday: ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'].indexOf(p.weekday!) };
}

// ---------- §5.2 timing ----------
export const DEFAULT_USUAL = hhmm('19:30');
export const STREAK_LATEST = hhmm('20:30');
const JUMUAH: [number, number] = [hhmm('12:30'), hhmm('14:00')];
const WINDOW = 15; // the send window is ±15 min around the send time

const NIGHT_END = hhmm('04:00'); // a start before 04:00 belongs to the evening before
/** Median local minute of the student's activity starts (last 21 days) minus 30 min; 19:30 with fewer than 4 of them. Starts before
 *  04:00 count as 24:00+, so a night owl's 23:50 and 00:10 give midnight, not noon. */
export function usualMinute(starts: number[]): number {
  if (starts.length < 4) return DEFAULT_USUAL;
  const s = starts.map((m) => (m < NIGHT_END ? m + DAY_MIN : m)).sort((a, b) => a - b);
  const mid = s.length >> 1;
  const median = s.length % 2 ? s[mid]! : Math.round((s[mid - 1]! + s[mid]!) / 2);
  return (median - 30 + DAY_MIN) % DAY_MIN;
}

const inRange = (m: number, [a, b]: [number, number]) => (a <= b ? m >= a && m < b : m >= a || m < b);
/** No nudge now: quiet hours (default 22:30–08:00, across midnight) or, on Friday, Jumu'ah 12:30–14:00. */
export const blocked = (minute: number, weekday: number, quiet: [number, number]) => inRange(minute, quiet) || (weekday === 5 && inRange(minute, JUMUAH));

/** The send time nearest `want` whose whole ±15 min window is outside quiet hours and Jumu'ah; null when no such time exists.
 *  `notLater`: only `want` or earlier the same day (the streak saver's 20:30 at the latest), never wrapped back past midnight. */
export function placeSend(want: number, weekday: number, quiet: [number, number], notLater = false): number | null {
  const clear = (t: number) => {
    for (let m = t - WINDOW; m < t + WINDOW; m++) if (blocked((m + DAY_MIN) % DAY_MIN, weekday, quiet)) return false;
    return true;
  };
  for (let d = 0; d <= DAY_MIN / 2; d += 5)
    for (const t of d ? (notLater ? [want - d] : [want - d, want + d]) : [want]) {
      if (notLater && t < 0) return null;
      const m = (t + DAY_MIN) % DAY_MIN;
      if (clear(m)) return m;
    }
  return null;
}

/** Today's send time (§5.2): the student's chosen "Best time to remind me", else the usual time from `starts` (local minutes); with an
 *  at-risk streak and no chosen time, 20:30 at the latest (§4 #3); then moved clear of quiet hours and Jumu'ah. */
export function sendMinute(o: { remindAt: string | null; starts: number[]; atRisk: boolean; weekday: number; quiet: [number, number] }): number | null {
  if (o.remindAt) return placeSend(hhmm(o.remindAt), o.weekday, o.quiet);
  const usual = usualMinute(o.starts);
  // at risk: 20:30 at the latest, and a usual time after midnight (a night owl) is later than that too; moved earlier, or (a usual
  // time in the morning quiet hours) to the nearest clear time after it, still by 20:30
  if (o.atRisk) {
    const want = usual < NIGHT_END ? STREAK_LATEST : Math.min(usual, STREAK_LATEST);
    const t = placeSend(want, o.weekday, o.quiet, true) ?? placeSend(want, o.weekday, o.quiet);
    return t != null && t <= STREAK_LATEST ? t : null;
  }
  return placeSend(usual, o.weekday, o.quiet);
}

/** `now` is inside the ±15 min window around `send` (minutes of the day, across midnight). */
export const inWindow = (now: number, send: number) => {
  const d = ((now - send + DAY_MIN * 1.5) % DAY_MIN) - DAY_MIN / 2;
  return d >= -WINDOW && d < WINDOW;
};

/** The student's zone from the stored settings row: timeZone, Asia/Dhaka when unset or unknown (as streaks.ts). */
export const zoneIn = (data: unknown) => {
  const tz = (data as { timeZone?: unknown } | null)?.timeZone;
  return typeof tz === 'string' && isTimeZone(tz) ? tz : 'Asia/Dhaka';
};
export const quietOf = (s: Pick<Settings, 'quietStart' | 'quietEnd'>): [number, number] => [hhmm(s.quietStart), hhmm(s.quietEnd)];

/** At delivery (push.ts): the type is still switched on, it isn't quiet hours or Jumu'ah, and the FCM ttl (seconds) so a phone that was
 *  offline doesn't show it once those start (at most 1 h, at least 1 min). null = don't send. */
export function nudgeTtl(type: NudgeType, s: Settings, timeZone: string, now: Date): number | null {
  const c = localClock(now, timeZone);
  if (!s[NUDGE_SWITCH[type]] || blocked(c.minute, c.weekday, quietOf(s))) return null;
  let m = 1;
  while (m < 60 && !blocked((c.minute + m) % DAY_MIN, c.weekday, quietOf(s))) m++;
  return m * 60;
}

// ---------- §5.4 back-off and §2 caps ----------
/** P1/P2 pushes in a row not opened (since the student last opened a push, the app, or practised) → level 0–3. */
export const backoffLevel = (unopened: number): 0 | 1 | 2 | 3 => (unopened >= 8 ? 3 : unopened >= 5 ? 2 : unopened >= 3 ? 1 : 0);
/** Types still sent at level 2 and 3 (weekly recap joins in round D). */
const HIGH_VALUE: readonly NudgeType[] = ['pattern', 'exam'];
export const allowedAt = (level: number, type: NudgeType) => (level < 2 ? type !== 'goodbye' : level === 2 ? HIGH_VALUE.includes(type) : type === 'exam');

export const DAILY_CAP = 1;
export const WEEKLY_CAP = 5;
/** Whether another nudge may go today. `sentDays` = the local days of nudges in the last 7×24 h (one per nudge). */
export function underCaps(level: number, today: string, sentDays: string[]): boolean {
  if (sentDays.filter((d) => d === today).length >= DAILY_CAP) return false;
  if (sentDays.length >= (level >= 2 ? 2 : WEEKLY_CAP)) return false;
  if (level === 1) {
    const yesterday = new Date(Date.parse(`${today}T00:00:00Z`) - 864e5).toISOString().slice(0, 10);
    if (sentDays.includes(yesterday)) return false; // every other day
  }
  return true;
}

// ---------- §1 holdout ----------
/** The stable 10%: a hash of the user id, so a student is always in or always out. */
export const isHoldout = (userId: string) => createHash('sha256').update(`holdout-v1:${userId}`).digest().readUInt32BE(0) % 100 < 10;

// ---------- §5.3 choosing the type ----------
/** noDrill: fewer than 5 cards, so no copy that only offers drills; noFomo: a saved freeze covers tonight, so no FOMO copy (templates.ts fits). */
export type Candidate = { type: NudgeType; vars: Record<string, string>; route: string; noDrill?: boolean; noFomo?: boolean };
export type TypeHistory = { sent: number; ignored: number; lastSentDaysAgo: number | null };

/** priority × freshness × (1 − fatigue); fatigue = ignored share of this type in the last 14 days, smoothed as ignored / (sent + 1) so
 *  one ignored of one is 0.5, not 1, and a type is never scored out for good (§5.3). */
export function score(c: Candidate, h: TypeHistory | undefined): number {
  const fresh = h?.lastSentDaysAgo == null ? 1 : Math.min(1, h.lastSentDaysAgo / COOLDOWN[c.type]);
  const fatigue = h ? h.ignored / (h.sent + 1) : 0;
  return PRIORITY[c.type] * fresh * (1 - fatigue);
}
export function pickCandidate(cands: Candidate[], history: Partial<Record<NudgeType, TypeHistory>>): Candidate | null {
  let best: Candidate | null = null;
  let top = 0;
  for (const c of cands) {
    const s = score(c, history[c.type]);
    if (s > top) [best, top] = [c, s];
  }
  return best;
}

// ---------- §5.5 Thompson sampling over templates ----------
export type Rng = () => number;
const normal = (rng: Rng) => Math.sqrt(-2 * Math.log(1 - rng())) * Math.cos(2 * Math.PI * rng());
/** Marsaglia–Tsang gamma(a, 1); a < 1 through gamma(a + 1)·U^(1/a). */
function gamma(a: number, rng: Rng): number {
  if (a < 1) return gamma(a + 1, rng) * rng() ** (1 / a);
  const d = a - 1 / 3;
  const c = 1 / Math.sqrt(9 * d);
  for (;;) {
    let x: number;
    let v: number;
    do {
      x = normal(rng);
      v = 1 + c * x;
    } while (v <= 0);
    v = v * v * v;
    const u = rng();
    if (u < 1 - 0.0331 * x ** 4 || Math.log(u) < 0.5 * x * x + d * (1 - v + Math.log(v))) return d * v;
  }
}
export const sampleBeta = (a: number, b: number, rng: Rng = Math.random) => {
  const x = gamma(a, rng);
  return x / (x + gamma(b, rng));
};

export type Stats = { sends: number; wins: number }; // wins = practised within 2 h of the send
const PRIOR = 2; // pseudo-sends in a template's prior

/** Thompson sampling: each template draws from Beta(α, β) over its sends and wins, with a prior worth 2 sends at the type's average
 *  win rate (so a new template starts like the type's average and gets tried); the highest draw wins. */
export function pickTemplate<T extends { id: string }>(templates: T[], stats: Map<string, Stats>, rng: Rng = Math.random): T {
  let sends = 0;
  let wins = 0;
  for (const t of templates) {
    const s = stats.get(t.id);
    sends += s?.sends ?? 0;
    wins += s?.wins ?? 0;
  }
  const mean = (wins + 1) / (sends + 2);
  let best = templates[0]!;
  let top = -1;
  for (const t of templates) {
    const s = stats.get(t.id) ?? { sends: 0, wins: 0 };
    const x = sampleBeta(PRIOR * mean + s.wins, PRIOR * (1 - mean) + s.sends - s.wins, rng);
    if (x > top) [best, top] = [t, x];
  }
  return best;
}
