import { queryOptions } from '@tanstack/react-query';
import { call, client, type Schemas } from './api';
import { plural } from './format';
import { queryClient } from './query';

export type Streak = Schemas['Streak'];

/** GET /api/streak (guests too). Refreshed by `streakChanged()` after anything that can count as practice. */
export const streakQuery = queryOptions({ queryKey: ['streak'], queryFn: () => call(client.GET('/api/streak')), staleTime: 60_000 });

/** Call after a submit / drill session: the celebration watcher compares the streak before and after. */
export const streakChanged = () => void queryClient.invalidateQueries({ queryKey: ['streak'] });

/** The browser's local date as YYYY-MM-DD (same calendar the server uses when the time zone setting is synced). */
export const localDay = (d = new Date()) => d.toLocaleDateString('en-CA');

/** YYYY-MM-DD plus n days (calendar dates, no time zone). */
export const addDays = (d: string, n: number) => new Date(Date.parse(`${d}T00:00:00Z`) + n * 864e5).toISOString().slice(0, 10);

/** §3.4: after 18:00, not practised today, and a streak worth saving (2+). */
export const atRisk = (s: Pick<Streak, 'current' | 'today'>, hour = new Date().getHours()) => hour >= 18 && s.today === 'pending' && s.current >= 2;

const LINES = [
  (n: number) => `Day ${n}. You're on fire.`,
  (n: number) => `${n} days and counting.`,
  (n: number) => `Day ${n} done. Small steps, big band.`,
  (n: number) => `That's ${n} days in a row. Nice work.`,
  (n: number) => `Day ${n}. Your future self says thanks.`,
  (n: number) => `${n} days strong. Keep the rhythm.`,
  (n: number) => `Day ${n} in the bag.`,
  (n: number) => `Still going: ${n} days.`,
];

/** Short celebration copy; varies by day so it reads fresh each day. */
export function celebrationLine(n: number): string {
  if (n <= 1) return 'Day 1. A streak starts here.';
  if (n === 2) return 'Back-to-back days! 2 in a row.';
  return LINES[n % LINES.length]!(n);
}

const MILESTONES = [0, 3, 7, 14, 30, 50, 100, 200, 365]; // server streaks.ts, plus the 0 start

/** Share of the way from the last milestone to the next (0-1), or null past the last one. */
export function milestoneProgress({ current, nextMilestone }: Pick<Streak, 'current' | 'nextMilestone'>) {
  if (nextMilestone == null) return null;
  const prev = [...MILESTONES].reverse().find((m) => m <= current) ?? 0;
  return (current - prev) / (nextMilestone - prev);
}

/** "1 freeze saved · next at day 14"; no "next" when holding the maximum (2). */
export function freezesLine({ current, freezes }: Pick<Streak, 'current' | 'freezes'>) {
  const have = freezes ? `${plural(freezes, 'freeze')} saved` : 'No freezes saved';
  return freezes >= 2 ? `${have} (the most you can hold)` : `${have} · next at day ${(Math.floor(current / 7) + 1) * 7}`;
}

/** First vs latest analysed band, only when it went up (the share card shows progress, never a drop). */
export function bandJourney(trend: { overall: number }[] | undefined): [number, number] | null {
  const from = trend?.[0]?.overall;
  const to = trend?.at(-1)?.overall;
  return from != null && to != null && to > from ? [from, to] : null;
}

export const shareText = (n: number, band: [number, number] | null) =>
  `${n}-day streak on NextBand${band ? ` · Band ${band[0].toFixed(1)} → ${band[1].toFixed(1)}` : ''}`;

const SEEN = 'nb-streak-celebrated';
let seenInMemory = ''; // storage blocked: still once per page life

/** True the first time it is called on a given local day (then false until tomorrow). */
export function claimCelebration(day = localDay()): boolean {
  let last = seenInMemory;
  try {
    last = localStorage.getItem(SEEN) ?? last;
  } catch {
    /* blocked */
  }
  if (last === day) return false;
  seenInMemory = day;
  try {
    localStorage.setItem(SEEN, day);
  } catch {
    /* blocked */
  }
  return true;
}
