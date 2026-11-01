import { readFileSync } from 'node:fs';
import { expect, it } from 'vitest';

// index.html paints the hero's static shell (#lp-pre) before any CSS loads, so it carries colour literals. They must
// stay on the landing ramp and brand colours in landing.css (:root --lp-g-*, --lp-brand-*), or the shell flashes a different grey on hydration.
const html = readFileSync('index.html', 'utf8') // vitest runs from apps/web;
const css = readFileSync('src/components/landing/landing.css', 'utf8');
const ramp = new Set([...css.matchAll(/--lp-(?:g-\d+|brand-[a-z-]+):\s*(#[0-9a-f]{6})/gi)].map((m) => m[1]!.toLowerCase()));

it('the static hero shell uses only colours from the landing ramp', () => {
  const shell = html.slice(html.indexOf('#lp-pre { display: none; }'), html.indexOf('</style>', html.indexOf('#lp-pre { display: none; }')));
  const used = [...shell.matchAll(/#[0-9a-f]{6}\b/gi)].map((m) => m[0].toLowerCase());
  expect(used.length).toBeGreaterThan(4);
  for (const c of used) expect(ramp.has(c), `${c} is not in the landing ramp`).toBe(true);
});
