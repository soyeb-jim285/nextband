import { describe, expect, it } from 'vitest';
import { DEFAULT_SETTINGS } from '../settings';
import {
  allowedAt, backoffLevel, blocked, hhmm, inWindow, isHoldout, localClock, nudgeTtl, pickCandidate, pickTemplate, placeSend, sampleBeta, sendMinute, underCaps, usualMinute,
  type Candidate, type Stats,
} from './rules';

const QUIET: [number, number] = [hhmm('22:30'), hhmm('08:00')];
const THU = 4;
const FRI = 5;
const t = (s: string) => hhmm(s);
/** Seeded PRNG (mulberry32), so bandit tests are fixed. */
const seeded = (seed: number) => () => {
  seed = (seed + 0x6d2b79f5) | 0;
  let x = Math.imul(seed ^ (seed >>> 15), 1 | seed);
  x = (x + Math.imul(x ^ (x >>> 7), 61 | x)) ^ x;
  return ((x ^ (x >>> 14)) >>> 0) / 4294967296;
};
const settings = { ...DEFAULT_SETTINGS, timeZone: 'Asia/Dhaka' };

describe('local clock', () => {
  it('is the student zone, not UTC', () => {
    // 18:30Z Thursday = 00:30 Friday in Dhaka (UTC+6)
    expect(localClock(new Date('2026-11-05T18:30:00Z'), 'Asia/Dhaka')).toEqual({ day: '2026-11-06', minute: 30, weekday: FRI });
    expect(localClock(new Date('2026-11-05T18:30:00Z'), 'UTC')).toEqual({ day: '2026-11-05', minute: t('18:30'), weekday: THU });
  });
});

describe('quiet hours and Jumuah', () => {
  it('quiet hours run across midnight', () => {
    for (const m of ['22:30', '23:59', '00:00', '03:00', '07:59']) expect(blocked(t(m), THU, QUIET), m).toBe(true);
    for (const m of ['22:29', '08:00', '12:00', '19:30']) expect(blocked(t(m), THU, QUIET), m).toBe(false);
  });
  it('quiet hours inside one day work too', () => {
    const q: [number, number] = [t('01:00'), t('06:00')];
    expect(blocked(t('00:59'), THU, q)).toBe(false);
    expect(blocked(t('01:00'), THU, q)).toBe(true);
    expect(blocked(t('06:00'), THU, q)).toBe(false);
  });
  it('Friday 12:30–14:00 is Jumuah; other days are not', () => {
    expect(blocked(t('12:29'), FRI, QUIET)).toBe(false);
    expect(blocked(t('12:30'), FRI, QUIET)).toBe(true);
    expect(blocked(t('13:59'), FRI, QUIET)).toBe(true);
    expect(blocked(t('14:00'), FRI, QUIET)).toBe(false);
    expect(blocked(t('13:00'), THU, QUIET)).toBe(false);
  });
  it('moves a send time to the nearest time whose whole ±15 min window is clear', () => {
    expect(placeSend(t('19:30'), THU, QUIET)).toBe(t('19:30'));
    expect(placeSend(t('23:00'), THU, QUIET)).toBe(t('22:15')); // late owl: just before quiet hours
    expect(placeSend(t('22:20'), THU, QUIET)).toBe(t('22:15'));
    expect(placeSend(t('07:00'), THU, QUIET)).toBe(t('08:15')); // early bird: just after
    expect(placeSend(t('13:00'), FRI, QUIET)).toBe(t('12:15')); // Jumuah: nearer edge
    expect(placeSend(t('13:30'), FRI, QUIET)).toBe(t('14:15'));
    expect(placeSend(t('13:30'), THU, QUIET)).toBe(t('13:30'));
    expect(placeSend(t('12:00'), THU, [t('00:00'), t('23:50')])).toBeNull(); // no 30-minute gap left
  });
  it('the window is ±15 min and wraps midnight', () => {
    expect(inWindow(t('19:15'), t('19:30'))).toBe(true);
    expect(inWindow(t('19:44'), t('19:30'))).toBe(true);
    expect(inWindow(t('19:45'), t('19:30'))).toBe(false);
    expect(inWindow(t('19:14'), t('19:30'))).toBe(false);
    expect(inWindow(t('00:05'), t('23:55'))).toBe(true);
    expect(inWindow(t('23:40'), t('00:05'))).toBe(false);
    expect(inWindow(t('23:55'), t('00:05'))).toBe(true);
  });
  it('the delivery ttl ends before quiet hours or Jumuah, and a blocked or switched-off type is not sent', () => {
    const at = (iso: string) => new Date(iso);
    expect(nudgeTtl('streak', settings, 'Asia/Dhaka', at('2026-11-05T14:00:00Z'))).toBe(3600); // 20:00 Thu
    expect(nudgeTtl('streak', settings, 'Asia/Dhaka', at('2026-11-05T16:00:00Z'))).toBe(1800); // 22:00: 30 min to quiet hours
    expect(nudgeTtl('streak', settings, 'Asia/Dhaka', at('2026-11-05T17:00:00Z'))).toBeNull(); // 23:00
    expect(nudgeTtl('streak', settings, 'Asia/Dhaka', at('2026-11-06T06:10:00Z'))).toBe(1200); // Fri 12:10
    expect(nudgeTtl('streak', settings, 'Asia/Dhaka', at('2026-11-06T06:40:00Z'))).toBeNull(); // Fri 12:40
    expect(nudgeTtl('streak', { ...settings, notifyStreak: false }, 'Asia/Dhaka', at('2026-11-05T14:00:00Z'))).toBeNull();
    expect(nudgeTtl('drills', { ...settings, notifyStreak: false }, 'Asia/Dhaka', at('2026-11-05T14:00:00Z'))).toBe(3600);
  });
});

describe('usual time', () => {
  it('is the median start minus 30 min, 19:30 with fewer than 4 starts', () => {
    expect(usualMinute([])).toBe(t('19:30'));
    expect(usualMinute([t('09:00'), t('09:00'), t('09:00')])).toBe(t('19:30'));
    expect(usualMinute([t('20:00'), t('21:00'), t('20:10'), t('20:20')])).toBe(t('19:45'));
    expect(usualMinute([t('06:00'), t('07:00'), t('07:10'), t('22:00'), t('07:20')])).toBe(t('06:40'));
    expect(usualMinute([t('00:10'), t('00:10'), t('00:20'), t('00:20')])).toBe(t('23:45'));
  });
  it('the send time: chosen time first, an at-risk streak by 20:30, clear of quiet hours', () => {
    const late = [t('21:30'), t('21:40'), t('21:50'), t('22:00')]; // usual 21:15
    const base = { remindAt: null, starts: late, atRisk: false, weekday: THU, quiet: QUIET };
    expect(sendMinute(base)).toBe(t('21:15'));
    expect(sendMinute({ ...base, atRisk: true })).toBe(t('20:30'));
    expect(sendMinute({ ...base, starts: [], atRisk: true })).toBe(t('19:30')); // already before 20:30
    expect(sendMinute({ ...base, remindAt: '21:00', atRisk: true })).toBe(t('21:00')); // the student's own choice wins
    expect(sendMinute({ ...base, remindAt: '13:00', weekday: FRI })).toBe(t('12:15'));
    expect(sendMinute({ ...base, remindAt: '23:30' })).toBe(t('22:15'));
  });
});

describe('back-off and caps', () => {
  it('levels by unopened in a row', () => {
    expect([0, 2, 3, 4, 5, 7, 8, 20].map(backoffLevel)).toEqual([0, 0, 1, 1, 2, 2, 3, 3]);
  });
  it('level 2 keeps the high-value types, level 3 only the exam countdown, goodbye is never a scored pick', () => {
    expect(allowedAt(0, 'drills')).toBe(true);
    expect(allowedAt(1, 'streak')).toBe(true);
    expect(allowedAt(2, 'streak')).toBe(false);
    expect(allowedAt(2, 'pattern')).toBe(true);
    expect(allowedAt(2, 'exam')).toBe(true);
    expect(allowedAt(3, 'pattern')).toBe(false);
    expect(allowedAt(3, 'exam')).toBe(true);
    expect(allowedAt(0, 'goodbye')).toBe(false);
  });
  it('1 a day, 5 a week, every other day at level 1, 2 a week from level 2', () => {
    const today = '2026-11-05';
    expect(underCaps(0, today, [])).toBe(true);
    expect(underCaps(0, today, [today])).toBe(false);
    expect(underCaps(0, today, ['2026-10-30', '2026-10-31', '2026-11-01', '2026-11-02'])).toBe(true);
    expect(underCaps(0, today, ['2026-10-30', '2026-10-31', '2026-11-01', '2026-11-02', '2026-11-04'])).toBe(false);
    expect(underCaps(1, today, ['2026-11-04'])).toBe(false);
    expect(underCaps(1, today, ['2026-11-03'])).toBe(true);
    expect(underCaps(0, today, ['2026-11-04'])).toBe(true);
    expect(underCaps(2, today, ['2026-11-01'])).toBe(true);
    expect(underCaps(2, today, ['2026-11-01', '2026-11-03'])).toBe(false);
    expect(underCaps(3, today, ['2026-11-01', '2026-11-03'])).toBe(false);
  });
});

describe('holdout', () => {
  it('is stable per user and about 10%', () => {
    const ids = Array.from({ length: 20000 }, (_, i) => `user-${i}`);
    const inside = ids.filter(isHoldout);
    expect(inside.length / ids.length).toBeGreaterThan(0.09);
    expect(inside.length / ids.length).toBeLessThan(0.11);
    expect(ids.slice(0, 500).map(isHoldout)).toEqual(ids.slice(0, 500).map(isHoldout));
    expect(isHoldout(inside[0]!)).toBe(true);
  });
});

describe('choosing the type', () => {
  const c = (type: Candidate['type']): Candidate => ({ type, vars: {}, route: '/' });
  it('P1 beats P2, fatigue and a recent send of the same type lower it', () => {
    expect(pickCandidate([c('drills'), c('streak')], {})?.type).toBe('streak');
    // pattern sent 2 days ago (cooldown 7) and ignored: 2 × 2/7 × 0.5 < drills 1
    expect(pickCandidate([c('pattern'), c('drills')], { pattern: { sent: 1, ignored: 1, lastSentDaysAgo: 2 } })?.type).toBe('drills');
    // streak every day is normal (cooldown 1): still first when opened
    expect(pickCandidate([c('streak'), c('drills')], { streak: { sent: 3, ignored: 0, lastSentDaysAgo: 1 } })?.type).toBe('streak');
    expect(pickCandidate([c('drills')], { drills: { sent: 1, ignored: 0, lastSentDaysAgo: 0 } })).toBeNull(); // sent today: score 0
    expect(pickCandidate([], {})).toBeNull();
  });
});

describe('Thompson sampling', () => {
  it('Beta draws have the right mean, small shapes too', () => {
    const rng = seeded(1);
    for (const [a, b] of [[2, 8], [0.4, 0.6], [30, 10]] as const) {
      let sum = 0;
      for (let i = 0; i < 20000; i++) sum += sampleBeta(a, b, rng);
      expect(sum / 20000).toBeCloseTo(a / (a + b), 2);
    }
  });
  it('picks the template that gets practice, still tries a new one, and is uniform with no data', () => {
    const rng = seeded(7);
    const tpl = [{ id: 'a' }, { id: 'b' }, { id: 'c' }];
    const count = (stats: Map<string, Stats>, list = tpl) => {
      const n: Record<string, number> = {};
      for (let i = 0; i < 3000; i++) {
        const id = pickTemplate(list, stats, rng).id;
        n[id] = (n[id] ?? 0) + 1;
      }
      return n;
    };
    const strong = count(new Map([['a', { sends: 200, wins: 80 }], ['b', { sends: 200, wins: 20 }], ['c', { sends: 200, wins: 20 }]]));
    expect(strong.a! / 3000).toBeGreaterThan(0.97);
    // c is new: its prior is the type's average (~25%), so it gets tried while a and b are equal
    const fresh = count(new Map([['a', { sends: 200, wins: 50 }], ['b', { sends: 200, wins: 50 }]]));
    expect(fresh.c! / 3000).toBeGreaterThan(0.1);
    const none = count(new Map());
    for (const id of ['a', 'b', 'c']) expect(none[id]! / 3000).toBeGreaterThan(0.28);
    expect(pickTemplate([{ id: 'only' }], new Map(), rng).id).toBe('only');
  });
});
