import { afterEach, expect, test } from 'vitest';
import { inPlayApp } from './inApp';

afterEach(() => {
  sessionStorage.clear();
  window.history.replaceState(null, '', '/');
});

test('a tab opened from the Play app stays in app mode after navigating', () => {
  expect(inPlayApp()).toBe(false);
  window.history.replaceState(null, '', '/terms?app=play');
  expect(inPlayApp()).toBe(true);
  window.history.replaceState(null, '', '/pricing');
  expect(inPlayApp()).toBe(true);
});
