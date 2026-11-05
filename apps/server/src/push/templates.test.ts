import { checkPush, fillTemplate } from '@ielts/core';
import { expect, it } from 'vitest';
import { NUDGE_TYPES, type NudgeType } from './rules';
import { nudgeTemplates } from './templates';

// The vars planner.ts candidates() produces for each type (the fewest it can have): every type must have sendable copy in both languages.
const VARS: Record<NudgeType, Record<string, string>> = {
  streak: { streak: '2' }, milestone: { milestone: '7' }, drills: { due: '5' }, pattern: { pattern: 'Articles', skill: 'Writing', hits: '3', of: '4' },
  exam: { days: '14' }, cold: { skill: 'Speaking', idle: '10' }, winback: { idle: '3' }, goodbye: {}, mock: { parts: 'Reading and Writing' },
};
const sendable = (type: NudgeType, vars: Record<string, string>, lang: 'en' | 'banglish') =>
  nudgeTemplates(type, vars, lang).filter((t) => !checkPush({ title: fillTemplate(t.title, vars), body: fillTemplate(t.body, vars) }).length);

it('every nudge type has sendable copy with the facts the planner fills', () => {
  for (const type of NUDGE_TYPES) for (const lang of ['en', 'banglish'] as const) expect(sendable(type, VARS[type], lang).length, `${type} ${lang}`).toBeGreaterThan(2);
});

it('the exam countdown uses the tomorrow group on its last day, and band/weakest unlock more copy', () => {
  expect(sendable('exam', { days: '1' }, 'en').every((t) => t.id.startsWith('examtmr-'))).toBe(true);
  expect(sendable('exam', { days: '14' }, 'en').every((t) => t.id.startsWith('exam-en-'))).toBe(true);
  expect(sendable('exam', { days: '14', band: '6.5', weakest: 'Reading' }, 'en').length).toBeGreaterThan(sendable('exam', { days: '14' }, 'en').length);
});
