import { checkPush, fillTemplate, NUDGE_TEMPLATES, type NudgeType as Group } from '@ielts/core';
import { expect, it } from 'vitest';
import { PATTERN_LABEL } from './planner';
import { NUDGE_TYPES, type Candidate, type NudgeType } from './rules';
import { fits, GROUP, NOT_WIRED, nudgeTemplates } from './templates';

// The vars planner.ts candidates() produces for each type (the fewest it can have): every type must have sendable copy in both languages.
const VARS: Record<NudgeType, Record<string, string>> = {
  streak: { streak: '2' }, milestone: { milestone: '7' }, drills: { due: '5' }, pattern: { pattern: 'Article mistakes', skill: 'Writing', hits: '3', of: '4' },
  exam: { days: '14' }, cold: { skill: 'Speaking', idle: '10' }, winback: { idle: '3' }, goodbye: {}, mock: { parts: 'Reading and Writing' },
};
const LANGS = ['en', 'banglish'] as const;
const sendable = (type: NudgeType, vars: Record<string, string>, lang: 'en' | 'banglish', c: Partial<Candidate> = {}) =>
  nudgeTemplates(type, vars, lang).filter((t) => fits(t, c) && !checkPush({ title: fillTemplate(t.title, vars), body: fillTemplate(t.body, vars) }).length);

it('every nudge type has sendable copy with the facts the planner fills, also with no cards to drill and a freeze saved', () => {
  for (const type of NUDGE_TYPES)
    for (const lang of LANGS) {
      expect(sendable(type, VARS[type], lang).length, `${type} ${lang}`).toBeGreaterThan(2);
      expect(sendable(type, VARS[type], lang, { noDrill: true, noFomo: true }).length, `${type} ${lang} no drill`).toBeGreaterThan(2);
    }
});

it('no cards: no drill-only copy; a freeze: no FOMO copy', () => {
  const plain = sendable('streak', VARS.streak, 'en');
  const noDrill = sendable('streak', VARS.streak, 'en', { noDrill: true });
  expect(plain.some((t) => t.drill)).toBe(true);
  expect(noDrill.some((t) => t.drill)).toBe(false);
  expect(sendable('streak', VARS.streak, 'banglish', { noFomo: true }).some((t) => t.tone === 'fomo')).toBe(false);
});

it('every mistake label and every mock remainder fits every template of its type', () => {
  const sections = ['Listening', 'Reading', 'Writing', 'Speaking'];
  const parts = Array.from({ length: 15 }, (_, m) => sections.filter((_, i) => (m + 1) & (1 << i))).map((l) => (l.length > 1 ? `${l.slice(0, -1).join(', ')} and ${l.at(-1)}` : l[0]!));
  const all = [
    ...Object.values(PATTERN_LABEL).flatMap((pattern) => ['Writing', 'Speaking', 'Listening'].map((skill) => ['pattern', { pattern, skill, hits: '3', of: '4' }] as const)),
    ...parts.map((p) => ['mock', { parts: p }] as const),
  ];
  for (const [type, vars] of all)
    for (const lang of LANGS)
      for (const t of nudgeTemplates(type, vars, lang)) {
        const m = { title: fillTemplate(t.title, vars), body: fillTemplate(t.body, vars) };
        expect(checkPush(m), `${t.id} ${JSON.stringify(vars)}`).toEqual([]);
        expect([...m.body].length, `${t.id} ${JSON.stringify(vars)}`).toBeLessThanOrEqual(90);
      }
});

it('every template group is sent by some engine type, or listed as not wired yet', () => {
  const reached = new Set<Group>([...Object.values(GROUP), 'exam-tomorrow', ...NOT_WIRED]);
  expect([...reached].sort()).toEqual(Object.keys(NUDGE_TEMPLATES).sort());
});

it('the exam countdown uses the tomorrow group on its last day, and band/weakest unlock more copy', () => {
  expect(sendable('exam', { days: '1' }, 'en').every((t) => t.id.startsWith('examtmr-'))).toBe(true);
  expect(sendable('exam', { days: '14' }, 'en').every((t) => t.id.startsWith('exam-en-'))).toBe(true);
  expect(sendable('exam', { days: '14', band: '6.5', weakest: 'Reading' }, 'en').length).toBeGreaterThan(sendable('exam', { days: '14' }, 'en').length);
});
