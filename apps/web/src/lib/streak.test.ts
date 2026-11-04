import { expect, it } from 'vitest';
import { atRisk, bandJourney, celebrationLine, claimCelebration, freezesLine, shareText } from './streak';

it('flags a streak at risk only after 18:00, pending, and 2+ days', () => {
  expect(atRisk({ current: 5, today: 'pending' }, 18)).toBe(true);
  expect(atRisk({ current: 5, today: 'pending' }, 17)).toBe(false);
  expect(atRisk({ current: 5, today: 'done' }, 21)).toBe(false);
  expect(atRisk({ current: 1, today: 'pending' }, 21)).toBe(false);
});

it('picks short copy that varies by day', () => {
  expect(celebrationLine(1)).toMatch(/Day 1/);
  expect(celebrationLine(2)).toMatch(/Back-to-back/);
  const lines = new Set(Array.from({ length: 8 }, (_, i) => celebrationLine(i + 3)));
  expect(lines.size).toBe(8);
  for (const n of [3, 12, 100]) expect(celebrationLine(n)).toContain(String(n));
});

it('describes freezes and the next one', () => {
  expect(freezesLine({ current: 12, freezes: 1 })).toBe('1 freeze saved · next at day 14');
  expect(freezesLine({ current: 0, freezes: 0 })).toBe('No freezes saved · next at day 7');
  expect(freezesLine({ current: 14, freezes: 1 })).toBe('1 freeze saved · next at day 21');
  expect(freezesLine({ current: 30, freezes: 2 })).toMatch(/^2 freezes saved \(/);
});

it('builds the share text, with the band only when it went up', () => {
  expect(shareText(12, bandJourney([{ overall: 6.5 }, { overall: 6 }, { overall: 7 }]))).toBe('12-day streak on NextBand · Band 6.5 → 7.0');
  expect(shareText(7, bandJourney([{ overall: 7 }, { overall: 6.5 }]))).toBe('7-day streak on NextBand');
  expect(bandJourney(undefined)).toBeNull();
});

it('celebrates once per local day', () => {
  localStorage.clear();
  expect(claimCelebration('2026-11-05')).toBe(true);
  expect(claimCelebration('2026-11-05')).toBe(false);
  expect(claimCelebration('2026-11-06')).toBe(true);
});
