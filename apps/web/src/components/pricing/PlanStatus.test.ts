import { expect, test } from 'vitest';
import { actionLabel, planOf } from './PlanStatus';

const now = Date.parse('2026-11-02T00:00:00Z');
const inDays = (d: number) => new Date(now + d * 86_400_000).toISOString();

test('planOf sorts tiers and dates into states', () => {
  expect(planOf({ tier: 'community', subscribedUntil: null }, now).state).toBe('free');
  expect(planOf({ tier: 'subscriber', subscribedUntil: inDays(20) }, now)).toMatchObject({ state: 'active', daysLeft: 20 });
  expect(planOf({ tier: 'subscriber', subscribedUntil: inDays(7) }, now).state).toBe('ending');
  expect(planOf({ tier: 'subscriber', subscribedUntil: inDays(-1) }, now).state).toBe('free'); // stale cache after the end
  expect(actionLabel(planOf({ tier: 'unlimited', subscribedUntil: null }, now))).toBeNull();
  expect(actionLabel(planOf({ tier: 'guest', subscribedUntil: null }, now))).toBeNull();
});
