import { createRoute, z } from '@hono/zod-openapi';
import { and, count, desc, eq, inArray, lt } from 'drizzle-orm';
import { currentUser, requireAccount } from '../auth';
import { db } from '../db/client';
import { attempts, cards, mistakes, prompts } from '../db/schema';
import type { App } from '../types';
import { mistakeCard } from '../coach/drills';
import { CardSchema, inDeck, insertCards } from './cards';

const PAGE_SIZE = 30;
const json = <T extends z.ZodType>(schema: T, description: string) => ({ description, content: { 'application/json': { schema } } });
const ErrorSchema = z.object({ error: z.string() }).openapi('Error');
const authed = { tags: ['Mistakes'], security: [{ bearer: [] }], middleware: [requireAccount] };

const MistakeSchema = z
  .object({
    id: z.string(),
    attemptId: z.string(),
    skill: z.enum(['speaking', 'writing']),
    part: z.number(),
    promptTitle: z.string(),
    category: z.string(),
    original: z.string(),
    correction: z.string(),
    explanation: z.string(),
    time: z.number().nullable().openapi({ description: 'Seconds into the audio (speaking)' }),
    inDeck: z.boolean().openapi({ description: 'Already added to the review deck' }),
    createdAt: z.string(),
  })
  .openapi('Mistake');

const MistakeLog = z
  .object({
    groups: z.array(z.object({ category: z.string(), count: z.number() })).openapi({ description: 'All categories, most frequent first' }),
    items: z.array(MistakeSchema),
    total: z.number(),
    page: z.number(),
    pageSize: z.number(),
  })
  .openapi('MistakeLog');

const PatternSchema = z
  .object({
    category: z.string(),
    count: z.number(),
    tests: z.number().openapi({ description: 'Tests it appeared in' }),
    outOf: z.number().openapi({ description: 'Finished tests of the skills it appears in' }),
    lastAt: z.string(),
    recent: z.number().nullable().openapi({ description: 'Per test, over the newest 3 tests of those skills (null under 4 tests)' }),
    before: z.number().nullable().openapi({ description: 'Per test, over the 3 tests before those' }),
    example: z.object({ original: z.string(), correction: z.string() }),
    cards: z.number().openapi({ description: 'Drill cards in this category' }),
    due: z.number(),
  })
  .openapi('MistakePattern');

const per = (n: number, d: number) => (d ? Math.round((n / d) * 10) / 10 : null);

export function register(app: App) {
  app.openapi(
    createRoute({
      ...authed,
      method: 'get',
      path: '/api/mistakes/patterns',
      summary: 'Mistake categories as patterns: how often, in how many tests, the trend per test, and the drill cards behind each',
      responses: {
        200: json(
          z.object({
            patterns: z.array(PatternSchema),
            deck: z.array(z.object({ category: z.string(), cards: z.number(), due: z.number() })).openapi({ description: 'Drill cards per category, including categories with no mistake rows (Listening/Reading spelling)' }),
          }).openapi('MistakePatterns'),
          'Patterns',
        ),
      },
    }),
    async (c) => {
      const uid = currentUser(c).id;
      const [rows, tests, deck] = await Promise.all([
        db
          .select({ category: mistakes.category, attemptId: mistakes.attemptId, original: mistakes.original, correction: mistakes.correction, skill: attempts.skill, at: attempts.createdAt })
          .from(mistakes)
          .innerJoin(attempts, eq(attempts.id, mistakes.attemptId))
          .where(eq(mistakes.userId, uid))
          .orderBy(desc(attempts.createdAt))
          .limit(3000), // ponytail: newest 3000 mistakes; an older tail only shifts the all-time counts
        db.select({ id: attempts.id, skill: attempts.skill }).from(attempts).where(and(eq(attempts.userId, uid), eq(attempts.status, 'done'))).orderBy(desc(attempts.createdAt)).limit(300),
        db.select({ category: cards.category, due: cards.due }).from(cards).where(and(eq(cards.userId, uid), eq(cards.suspended, false))),
      ]);
      const now = Date.now();
      const by = new Map<string, typeof rows>();
      for (const r of rows) by.set(r.category, [...(by.get(r.category) ?? []), r]);
      const patterns = [...by].map(([category, ms]) => {
        const skills = new Set(ms.map((m) => m.skill));
        const mine = tests.filter((t) => skills.has(t.skill)); // newest first
        const n = (ids: string[]) => ms.filter((m) => ids.includes(m.attemptId)).length;
        const [recent, before] = [mine.slice(0, 3), mine.slice(3, 6)].map((t) => t.map((x) => x.id));
        const inCat = deck.filter((d) => d.category === category);
        return {
          category,
          count: ms.length,
          tests: new Set(ms.map((m) => m.attemptId)).size,
          outOf: mine.length,
          lastAt: ms[0]!.at.toISOString(),
          recent: before!.length ? per(n(recent!), recent!.length) : null,
          before: before!.length ? per(n(before!), before!.length) : null,
          example: { original: ms[0]!.original, correction: ms[0]!.correction },
          cards: inCat.length,
          due: inCat.filter((d) => d.due.getTime() <= now).length,
        };
      });
      patterns.sort((a, b) => b.tests - a.tests || b.count - a.count);
      const byCat = new Map<string, { category: string; cards: number; due: number }>();
      for (const d of deck) {
        if (!d.category) continue;
        const v = byCat.get(d.category) ?? { category: d.category, cards: 0, due: 0 };
        v.cards++;
        if (d.due.getTime() <= now) v.due++;
        byCat.set(d.category, v);
      }
      return c.json({ patterns, deck: [...byCat.values()] }, 200);
    },
  );

  app.openapi(
    createRoute({
      ...authed,
      method: 'get',
      path: '/api/mistakes/recurring/{attemptId}',
      summary: "One test's mistakes by category, each with how many of the 10 earlier tests (same skill) had it too, and the drill cards it added",
      request: { params: z.object({ attemptId: z.string().openapi({ param: { name: 'attemptId', in: 'path' } }) }) },
      responses: {
        200: json(
          z
            .object({
              items: z.array(z.object({ category: z.string(), here: z.number(), before: z.number(), outOf: z.number() })),
              added: z.number().openapi({ description: 'Drill cards made from this test' }),
            })
            .openapi('RecurringMistakes'),
          'Recurring',
        ),
        404: json(ErrorSchema, 'Not found'),
      },
    }),
    async (c) => {
      const uid = currentUser(c).id;
      const id = c.req.valid('param').attemptId;
      const [a] = await db.select({ skill: attempts.skill, at: attempts.createdAt }).from(attempts).where(and(eq(attempts.id, id), eq(attempts.userId, uid)));
      if (!a) return c.json({ error: 'Attempt not found' }, 404);
      const [here, earlier, [{ added } = { added: 0 }]] = await Promise.all([
        db.select({ category: mistakes.category, n: count() }).from(mistakes).where(eq(mistakes.attemptId, id)).groupBy(mistakes.category),
        db.select({ id: attempts.id }).from(attempts).where(and(eq(attempts.userId, uid), eq(attempts.skill, a.skill), eq(attempts.status, 'done'), lt(attempts.createdAt, a.at))).orderBy(desc(attempts.createdAt)).limit(10),
        db.select({ added: count() }).from(cards).where(and(eq(cards.userId, uid), eq(cards.attemptId, id))),
      ]);
      const ids = earlier.map((e) => e.id);
      const prior = ids.length && here.length
        ? await db.select({ category: mistakes.category, attemptId: mistakes.attemptId }).from(mistakes).where(and(inArray(mistakes.attemptId, ids), inArray(mistakes.category, here.map((h) => h.category))))
        : [];
      const items = here
        .map((h) => ({ category: h.category, here: h.n, before: new Set(prior.filter((p) => p.category === h.category).map((p) => p.attemptId)).size, outOf: ids.length }))
        .sort((x, y) => y.before - x.before || y.here - x.here);
      return c.json({ items, added }, 200);
    },
  );

  app.openapi(
    createRoute({
      ...authed,
      method: 'get',
      path: '/api/mistakes',
      summary: 'Error log: counts per category + newest mistakes (optionally one category), 30 per page',
      request: { query: z.object({ category: z.string().max(60).optional(), page: z.coerce.number().int().min(1).default(1) }) },
      responses: { 200: json(MistakeLog, 'Mistakes') },
    }),
    async (c) => {
      const uid = currentUser(c).id;
      const { category, page } = c.req.valid('query');
      const where = and(eq(mistakes.userId, uid), category ? eq(mistakes.category, category) : undefined);
      const [groups, [{ total } = { total: 0 }], rows] = await Promise.all([
        db.select({ category: mistakes.category, count: count() }).from(mistakes).where(eq(mistakes.userId, uid)).groupBy(mistakes.category).orderBy(desc(count()), mistakes.category),
        db.select({ total: count() }).from(mistakes).where(where),
        db
          .select({ m: mistakes, skill: attempts.skill, part: attempts.part, promptTitle: prompts.title })
          .from(mistakes)
          .innerJoin(attempts, eq(attempts.id, mistakes.attemptId))
          .innerJoin(prompts, eq(prompts.id, attempts.promptId))
          .where(where)
          .orderBy(desc(mistakes.createdAt), mistakes.id)
          .limit(PAGE_SIZE)
          .offset((page - 1) * PAGE_SIZE),
      ]);
      const added = await inDeck(uid, rows.map((r) => mistakeCard(r.m)));
      const items = rows.map(({ m: { userId: _u, errorId: _e, createdAt, ...m }, ...r }, k) => ({ ...m, ...r, inDeck: added[k]!, createdAt: createdAt.toISOString() }));
      return c.json({ groups, items, total, page, pageSize: PAGE_SIZE }, 200);
    },
  );

  app.openapi(
    createRoute({
      ...authed,
      method: 'post',
      path: '/api/mistakes/{id}/card',
      summary: 'Add a mistake to the review deck (front: original, back: correction — explanation)',
      request: { params: z.object({ id: z.string().openapi({ param: { name: 'id', in: 'path' } }) }) },
      responses: { 201: json(CardSchema, 'Created'), 404: json(ErrorSchema, 'Not found') },
    }),
    async (c) => {
      const uid = currentUser(c).id;
      const [m] = await db.select().from(mistakes).where(and(eq(mistakes.id, c.req.valid('param').id), eq(mistakes.userId, uid)));
      if (!m) return c.json({ error: 'Mistake not found' }, 404);
      const [card] = await insertCards(uid, [mistakeCard(m)]);
      return c.json(card!, 201);
    },
  );
}
