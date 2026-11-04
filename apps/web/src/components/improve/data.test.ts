import { expect, it } from 'vitest';
import { answerOf, promptOf, sameAnswer } from './data';

it('splits every card shape into answer and note', () => {
  expect(answerOf('an umbrella — Use "an" before a vowel sound.')).toEqual({ answer: 'an umbrella', note: 'Use "an" before a vowel sound.' });
  expect(answerOf('make a mistake\n\nCollocation.')).toEqual({ answer: 'make a mistake', note: 'Collocation.' });
  expect(answerOf('Whitcombe\nYou wrote: Witcomb')).toEqual({ answer: 'Whitcombe', note: 'You wrote: Witcomb' });
  expect(answerOf('just this')).toEqual({ answer: 'just this', note: '' });
  expect(promptOf({ source: 'fix', front: 'Agreement\n\nThey was late' })).toEqual({ label: 'Agreement', text: 'They was late' });
  expect(promptOf({ source: 'mistake', front: 'Fix: "a apple"' })).toEqual({ label: null, text: 'a apple' });
  expect(promptOf({ source: 'mistake', front: "🎧 Listening · spell the word you heard: 'p__k'\nHint: 4 letters, starts with 'p'" })).toEqual({
    label: 'Listening · spell the word you heard',
    text: "p__k\nHint: 4 letters, starts with 'p'",
  });
  expect(promptOf({ source: 'mistake', front: "🎧 Listening · spell the word you heard: 'p__k' (4 letters)" })).toEqual({ label: 'Listening · spell the word you heard', text: 'p__k (4 letters)' });
  expect(sameAnswer('An  umbrella.', 'an umbrella')).toBe(true);
  expect(sameAnswer('a umbrella', 'an umbrella')).toBe(false);
});
