import { expect, it, vi } from 'vitest';
import { db } from '../db/client';
import { attempts, cards, mistakes } from '../db/schema';
import { req, seedPrompt, testUser } from '../test/helpers';

// Jev says "no" to anything with "fine" in it, throws on "boom" (an outage counts as yes), else yes.
vi.mock('../ai/openrouter', async (orig) => ({
  ...(await orig<typeof import('../ai/openrouter')>()),
  decide: vi.fn(async ({ state }: { state: { mistake: { original: string } } }) => {
    if (state.mistake.original.includes('boom')) throw new Error('down');
    return { drill: { noul: state.mistake.original.includes('fine') ? 0.1 : 0.9 } };
  }),
}));
const { chooseDrills } = await import('./drills');

const DAY = 864e5;
const err = (category: string, original: string) => ({ category, original, correction: `${original}!`, explanation: 'why' });
async function test(userId: string, daysAgo: number, errs: ReturnType<typeof err>[] = []) {
  const p = await seedPrompt();
  const [a] = await db.insert(attempts).values({ userId, promptId: p.id, skill: 'writing', part: 2, status: 'done', createdAt: new Date(Date.now() - daysAgo * DAY) }).returning();
  if (errs.length) await db.insert(mistakes).values(errs.map((e, i) => ({ userId, attemptId: a!.id, errorId: `e${i}`, ...e })));
  return a!.id;
}

it('chooseDrills: drops the undrillable, the known and the judged-no; recurring categories first; an outage still adds', async () => {
  const { user } = await testUser();
  await test(user.id, 3, [err('grammar.article', 'a old car')]);
  await test(user.id, 2, [err('grammar.article', 'a apple')]);
  await db.insert(cards).values({ userId: user.id, front: 'a apple', back: 'x', source: 'mistake', category: 'grammar.article' });
  const id = await test(user.id, 0);
  const picked = await chooseDrills(user.id, id, [
    err('lexis.collocation', 'do a mistake'),
    err('grammar.article', 'a umbrella'),
    err('grammar.article', 'a apple'), // already a card
    err('task.relevance', 'whole off-topic answer'),
    err('grammar.tense', 'this is fine'), // judged no
    err('grammar.plural', 'two boom'), // Jev down: kept
    { ...err('grammar.other', 'same'), correction: 'same' }, // nothing to fix
  ]);
  expect(picked.map((d) => d.front)).toEqual(['a umbrella', 'two boom', 'do a mistake']);
  expect(picked[0]).toMatchObject({ source: 'mistake', category: 'grammar.article', attemptId: id, back: 'a umbrella! — why' });
});

it('chooseDrills: at most 10 new cards a day', async () => {
  const { user } = await testUser();
  const old = await test(user.id, 0);
  await db.insert(cards).values(Array.from({ length: 8 }, (_, i) => ({ userId: user.id, front: `c${i}`, back: 'x', source: 'mistake', category: 'grammar.other', attemptId: old })));
  const id = await test(user.id, 0);
  expect(await chooseDrills(user.id, id, ['one', 'two', 'three'].map((w) => err('grammar.tense', w)))).toHaveLength(2);
});

it('patterns, recurring, due by category and skip', async () => {
  const u = await testUser();
  const uid = u.user.id;
  for (const d of [6, 5, 4, 3]) await test(uid, d, [err('grammar.article', `old ${d}`)]);
  const a = await test(uid, 1, [err('grammar.article', 'a umbrella'), err('grammar.article', 'a egg'), err('lexis.collocation', 'do a mistake')]);
  await db.insert(cards).values([
    { userId: uid, front: 'a umbrella', back: 'an umbrella — why', source: 'mistake', category: 'grammar.article', attemptId: a },
    { userId: uid, front: 'later', back: 'x', source: 'mistake', category: 'grammar.article', due: new Date(Date.now() + 5 * DAY) },
    { userId: uid, front: 'do a mistake', back: 'make a mistake — why', source: 'mistake', category: 'lexis.collocation', attemptId: a },
  ]);

  const { patterns } = (await (await req('/api/mistakes/patterns', { headers: u.headers })).json()) as { patterns: any[] };
  expect(patterns[0]).toMatchObject({ category: 'grammar.article', count: 6, tests: 5, outOf: 5, recent: 1.3, before: 1, cards: 2, due: 1, example: { original: 'a umbrella' } });
  expect(patterns[1]).toMatchObject({ category: 'lexis.collocation', tests: 1 });
  expect(((await (await req('/api/mistakes/patterns', { headers: u.headers })).json()) as any).deck).toContainEqual({ category: 'grammar.article', cards: 2, due: 1 });

  const rec = (await (await req(`/api/mistakes/recurring/${a}`, { headers: u.headers })).json()) as any;
  expect(rec).toEqual({ added: 2, items: [{ category: 'grammar.article', here: 2, before: 4, outOf: 4 }, { category: 'lexis.collocation', here: 1, before: 0, outOf: 4 }] });

  const due = async (q = '') => (await (await req(`/api/cards/due${q}`, { headers: u.headers })).json()) as { cards: any[]; total: number; deck: number };
  expect((await due('?category=grammar.article')).cards.map((c) => c.front)).toEqual(['a umbrella']);
  expect((await due('?category=grammar.article&practice=1')).total).toBe(2);
  const first = (await due('?category=grammar.article')).cards[0];
  expect(first.origin).toMatchObject({ skill: 'writing', part: 2 });
  expect((await req(`/api/cards/${first.id}/skip`, { method: 'POST', headers: u.headers })).status).toBe(200);
  expect(await due()).toMatchObject({ total: 1, deck: 2 });
  expect((await req(`/api/mistakes/recurring/${a}`, { headers: (await testUser()).headers })).status).toBe(404);
});
