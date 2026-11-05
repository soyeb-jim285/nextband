// STUB (round C engine): the template library is being written in parallel and replaces this file. Shape the planner needs:
// NUDGE_TEMPLATES[type][language] = [{ id, title, body }], {vars} filled by fillTemplate.
import type { PushLanguage } from '@ielts/core';
import type { NudgeType } from './rules';

export type NudgeTemplate = { id: string; title: string; body: string };
const one = (type: NudgeType, title: string, body: string) => ({ en: [{ id: `${type}-en-1`, title, body }], banglish: [{ id: `${type}-bn-1`, title, body }] });
export const NUDGE_TEMPLATES: Record<NudgeType, Record<PushLanguage, NudgeTemplate[]>> = {
  streak: one('streak', '🔥 {streak} days', 'One 2-minute drill keeps it alive.'),
  milestone: one('milestone', 'Tomorrow is day {milestone}', 'Big one. Practise tomorrow to reach it.'),
  drills: one('drills', '{due} mistakes ready to drill', 'They stick after 3 rounds.'),
  pattern: one('pattern', '{pattern}: {count} of your last {of} tests', 'A few drills on it?'),
  exam: one('exam', '{days} days to your IELTS', 'A full mock shows where you stand.'),
  cold: one('cold', 'Your {skill} has gone quiet', 'One test keeps it warm.'),
  winback: one('winback', 'Your practice is waiting', 'Pick up where you left off.'),
  goodbye: one('goodbye', "We'll stop reminding you", 'Your streak, drills and progress stay here when you are ready.'),
  mock: one('mock', 'Your mock is halfway done', '{left} left.'),
};
