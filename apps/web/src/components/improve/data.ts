import { queryOptions } from '@tanstack/react-query';
import { call, client, type Schemas } from '@/lib/api';

export type DrillCard = Schemas['Card'];
export type Pattern = Schemas['MistakePattern'];
export type DeckCategory = Schemas['MistakePatterns']['deck'][number];

/** Due drill cards. The plain call keeps the ['cards', 'due'] key the dashboard and nav share; a pattern or "ahead of schedule" adds to it. */
export const dueQuery = (o: { pattern?: string; ahead?: boolean } = {}) =>
  queryOptions({
    queryKey: o.pattern || o.ahead ? ['cards', 'due', { pattern: o.pattern ?? null, ahead: !!o.ahead }] : ['cards', 'due'],
    queryFn: () => call(client.GET('/api/cards/due', { params: { query: { category: o.pattern, practice: o.ahead ? '1' : undefined } } })),
    staleTime: 0,
  });

export const patternsQuery = queryOptions({ queryKey: ['mistakes', 'patterns'], queryFn: () => call(client.GET('/api/mistakes/patterns')), staleTime: 0 });

export const recurringQuery = (attemptId: string) =>
  queryOptions({ queryKey: ['mistakes', 'recurring', attemptId], queryFn: () => call(client.GET('/api/mistakes/recurring/{attemptId}', { params: { path: { attemptId } } })) });

/**
 * A card's back split into what to type and why. Mistake cards are "correction — why" (server mistakeCard) or "correction\n\nwhy"
 * (a result page's errorCard), fix cards "after\n\nwhy", spelling cards "word\nYou wrote: x".
 */
export function answerOf(back: string) {
  const m = / — |\n\n|\n(?=You wrote:)/.exec(back);
  return m ? { answer: back.slice(0, m.index).trim(), note: back.slice(m.index + m[0].length).trim() } : { answer: back.trim(), note: '' };
}

/** The text to correct: a fix card's "title\n\nsentence" splits into a label and the sentence; errorCard's `Fix: "x"` loses its wrapper. */
export function promptOf(c: Pick<DrillCard, 'front' | 'source'>) {
  const cut = c.source === 'fix' ? c.front.indexOf('\n\n') : -1;
  if (cut > 0) return { label: c.front.slice(0, cut), text: c.front.slice(cut + 2) };
  // Listening/Reading spelling (server lr.ts spellingCards): "🎧 Listening · spell the word you heard: 'p__k' (4 letters)"
  // older cards put the hint on a second line ("…: 'p__k'\nHint: 4 letters, starts with 'p'")
  const lr = /^(?:🎧|📖) (.+?): '(.+)'([\s\S]*)$/u.exec(c.front);
  if (lr) return { label: lr[1]!.charAt(0).toUpperCase() + lr[1]!.slice(1), text: `${lr[2]}${lr[3]}` };
  return { label: null, text: c.front.replace(/^Fix: "([\s\S]*)"$/, '$1') };
}

/** Same words, ignoring case, punctuation and spacing. */
const norm = (s: string) => s.toLowerCase().replace(/[^\p{L}\p{N}' ]+/gu, ' ').replace(/\s+/g, ' ').trim();
export const sameAnswer = (typed: string, answer: string) => norm(typed) === norm(answer);
