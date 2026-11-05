// The engine's nudge types read the round C template library in @ielts/core (push-templates.ts), which names its groups after the plan's types.
// Candidate vars use the library's fact names (NUDGE_FACTS); a template whose facts the student lacks fails checkPush (unfilled) and is skipped.
import { NUDGE_TEMPLATES, type NudgeType as Group, type PushLanguage } from '@ielts/core';
import type { NudgeType } from './rules';

const GROUP: Record<NudgeType, Group> = {
  streak: 'streak-saver', milestone: 'milestone-near', drills: 'drills-due', pattern: 'pattern', exam: 'exam', cold: 'cold', winback: 'win-back', goodbye: 'goodbye', mock: 'mock-ready',
};

/** The templates for a nudge; the exam countdown's last day has its own group ("1 days" reads wrong). */
export const nudgeTemplates = (type: NudgeType, vars: Record<string, string>, lang: PushLanguage) =>
  NUDGE_TEMPLATES[type === 'exam' && vars.days === '1' ? 'exam-tomorrow' : GROUP[type]][lang];
