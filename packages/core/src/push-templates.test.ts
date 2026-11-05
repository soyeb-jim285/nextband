import { expect, it } from 'vitest';
import { checkPush, fillTemplate, PUSH_TEMPLATES } from './notifications';
import { NUDGE_FACTS, NUDGE_TEMPLATES, NUDGE_VARS } from './push-templates';

const all = Object.entries(NUDGE_TEMPLATES).flatMap(([type, langs]) => Object.entries(langs).flatMap(([lang, list]) => list.map((t) => ({ type: type as keyof typeof NUDGE_VARS, lang, t }))));

it('every nudge template passes the push validator and the 90-character tone guide with the longest facts', () => {
  for (const { t } of all) {
    const m = { title: fillTemplate(t.title, NUDGE_FACTS), body: fillTemplate(t.body, NUDGE_FACTS) };
    expect(checkPush(m), t.id).toEqual([]);
    expect([...m.body].length, t.id).toBeLessThanOrEqual(90);
  }
});

it('each template lists exactly the variables it uses, and only facts its type has', () => {
  for (const { type, t } of all) {
    const used = new Set([...`${t.title} ${t.body}`.matchAll(/\{(\w+)\}/g)].map((m) => m[1]));
    expect([...used].sort(), t.id).toEqual([...t.vars].sort());
    for (const v of t.vars) expect(NUDGE_VARS[type], `${t.id} uses {${v}}`).toContain(v);
  }
});

it('every type has English and Banglish copy, ids are unique across all push copy and carry the language', () => {
  for (const [type, langs] of Object.entries(NUDGE_TEMPLATES)) {
    expect(langs.en.length, type).toBeGreaterThanOrEqual(6);
    expect(langs.banglish.length, type).toBeGreaterThanOrEqual(4);
  }
  for (const { lang, t } of all) expect(t.id, t.id).toContain(lang === 'en' ? '-en-' : '-bn-');
  const ids = [...all.map((x) => x.t.id), ...Object.values(PUSH_TEMPLATES).flatMap((l) => Object.values(l).flat().map((t) => t.id))];
  expect(new Set(ids).size).toBe(ids.length);
});

it('every template reads right with the smallest facts too (no "1 tests", no "0 drills")', () => {
  const MIN = { streak: '2', milestone: '3', due: '5', pattern: 'Spelling mistakes', skill: 'Reading', hits: '3', of: '3', days: '3', band: '4.0', idle: '3', weakest: 'Reading', active: '1', tests: '0', drills: '0', parts: 'Speaking' };
  for (const { t } of all) {
    const m = { title: fillTemplate(t.title, MIN), body: fillTemplate(t.body, MIN) };
    expect(checkPush(m), t.id).toEqual([]);
    expect(`${m.title} ${m.body}`, t.id).not.toMatch(/\b(0|1) (tests|drills|drill cards|cards|days)\b/);
  }
});

it('copy sent at the usual or chosen time (which can be the morning) never says tonight or evening', () => {
  for (const g of ['streak-saver', 'milestone-near', 'drills-due', 'pattern', 'exam', 'cold', 'win-back', 'goodbye', 'mock-ready'] as const)
    for (const t of [...NUDGE_TEMPLATES[g].en, ...NUDGE_TEMPLATES[g].banglish]) expect(`${t.title} ${t.body}`, t.id).not.toMatch(/\b(tonight|evening|shondh\w*|aj rate)\b/i);
});
