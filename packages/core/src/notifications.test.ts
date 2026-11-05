import { expect, it } from 'vitest';
import { checkPush, fillTemplate, PUSH_TEMPLATES, renderPush } from './notifications';

const LONGEST = { what: 'Speaking Part 2', band: '6.5', skill: 'Speaking' };

it('every template passes the push validator with the longest variables', () => {
  for (const [type, langs] of Object.entries(PUSH_TEMPLATES))
    for (const [lang, list] of Object.entries(langs)) {
      expect(list.length, `${type}/${lang}`).toBeGreaterThan(0);
      for (const t of list) expect(checkPush({ title: fillTemplate(t.title, LONGEST), body: fillTemplate(t.body, LONGEST) }), t.id).toEqual([]);
    }
});

it('template ids are unique', () => {
  const ids = Object.values(PUSH_TEMPLATES).flatMap((l) => Object.values(l).flat().map((t) => t.id));
  expect(new Set(ids).size).toBe(ids.length);
});

it('push never sells or shames', () => {
  const bad = (body: string) => checkPush({ title: 'Hi', body });
  for (const s of ['Go Pro today', 'Upgrade now', 'Only ৳299', 'Just $3', 'Buy more tests', 'Subscribe for more', 'Visit our website', 'nextband.app/pricing', 'Tap the link', 'Pick a plan', 'Unlock mocks', 'See the price'])
    expect(bad(s).some((p) => p.startsWith('selling word')), s).toBe(true);
  for (const s of ['Ekhon kino', 'Pro kinun aj', 'Dam matro 299 taka', '299 tk te', 'Offer e pabe', 'bKash e pay koro'])
    expect(bad(s).some((p) => p.startsWith('selling word')), s).toBe(true);
  for (const s of ["Don't be lazy", 'We are disappointed', "You're failing", 'Your friends are ahead', "Don't fall into falling behind", 'Others are ahead of you',
    'Eto olosh keno?', 'Lojja lage na?', 'Faki dio na', 'Tumi pichiye porcho', 'Tomar bondhura egiye geche', 'Shobai egiye jacche'])
    expect(bad(s).some((p) => p.startsWith('shaming word')), s).toBe(true);
  // review round C probes: selling and guilt the first lists missed (the guardrail for §5.6 AI copy)
  for (const s of ['Practise unlimited today', 'Start your free trial', 'Limited time only', 'Get it on Play Store', 'Membership perks', 'NextBand Plus', 'Get 50% more', 'Use coupon NB10', 'Promo inside'])
    expect(bad(s).some((p) => p.startsWith('selling word')), s).toBe(true);
  for (const s of ['You broke your streak 😢', 'You lost your streak', "Don't give up now", 'No excuses tonight', "Why haven't you practised?", 'Why haven’t you practised?',
    'You missed yesterday', 'Tumi bhule gecho?', 'Keno practice koroni?', 'Streak hariye felecho', 'Streak is waiting 😞', 'Hmm 💔'])
    expect(bad(s).some((p) => p.startsWith('shaming word')), s).toBe(true);
  for (const s of ['You forgot your practice', 'Your drills feel forgotten', 'You ignored us', 'Stop slacking', 'A wasted week', "Don't quit now", "You're behind", 'Don’t abandon your goal'])
    expect(bad(s).some((p) => p.startsWith('shaming word')), s).toBe(true);
  // whole words only: progress, pronunciation, explanation are fine
  expect(bad('Your progress and pronunciation improved. Planned well')).toEqual([]);
});

it('limits length and emoji, and catches unfilled variables', () => {
  expect(checkPush({ title: 'x'.repeat(41), body: 'ok' })).toContain('title over 40 characters');
  expect(checkPush({ title: 'ok', body: 'x'.repeat(111) })).toContain('body over 110 characters');
  expect(checkPush({ title: 'ok 🎯', body: 'ok 🚀' })).toContain('more than 1 emoji');
  expect(checkPush({ title: 'Your {what}', body: 'ok' })).toContain('unfilled variable');
  expect(renderPush('result', 'en', LONGEST).title).toContain('6.5');
});
