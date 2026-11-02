import { isKnownPath } from '@ielts/core';
import { readFileSync } from 'node:fs';
import { expect, it } from 'vitest';

// The server answers paths outside core's route list with HTTP 404 (no soft 404s). A new web route must be added there too.
it('every web route is a known path for the server', () => {
  const tree = readFileSync('src/routeTree.gen.ts', 'utf8');
  const paths = [...new Set([...tree.matchAll(/fullPath: '([^']*)'/g)].map((m) => m[1]!.replace(/\$[^/]+/g, 'x')))];
  expect(paths.length).toBeGreaterThan(30);
  expect(paths.filter((p) => !isKnownPath(p))).toEqual([]);
  expect(isKnownPath('/nope')).toBe(false);
  expect(isKnownPath('/.well-known/ai-catalog.json')).toBe(false);
});
