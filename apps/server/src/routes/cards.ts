import { createRoute, z } from '@hono/zod-openapi';
import { review } from '@ielts/core';
import { and, asc, count, eq, inArray, lte } from 'drizzle-orm';
import { currentUser, requireAccount } from '../auth';
import { recordActivity } from '../streaks';
import { db } from '../db/client';
import { attempts, cards, prompts } from '../db/schema';
import type { Fix } from '../ai/types';
import type { App } from '../types';

const json = <T extends z.ZodType>(schema: T, description: string) => ({ description, content: { 'application/json': { schema } } });
const ErrorSchema = z.object({ error: z.string() }).openapi('Error');
const authed = { tags: ['Cards'], security: [{ bearer: [] }], middleware: [requireAccount] };

export const CardInput = z
  .object({
    front: z.string().trim().min(1).max(2000),
    back: z.string().trim().min(1).max(4000),
    source: z.enum(['mistake', 'vocab', 'fix']),
    category: z.string().max(60).nullish().openapi({ description: 'Mistake category (grammar.article, spelling, ...); drills can be narrowed to one' }),
  })
  .openapi('CardInput');
const Origin = z.object({ skill: z.enum(['speaking', 'writing']), part: z.number(), title: z.string(), at: z.string() }).openapi('CardOrigin');
export const CardSchema = CardInput.extend({
  id: z.string(),
  category: z.string().nullable(),
  attemptId: z.string().nullable(),
  origin: Origin.nullable().openapi({ description: 'The Speaking/Writing test the card came from, when known' }),
  ease: z.number(),
  interval: z.number().openapi({ description: 'Days' }),
  reps: z.number(),
  due: z.string(),
  createdAt: z.string(),
}).openapi('Card');

type CardRow = typeof cards.$inferSelect;
export const toCard = ({ userId: _u, suspended: _s, due, createdAt, source, ...c }: CardRow, origin: z.infer<typeof Origin> | null = null): z.infer<typeof CardSchema> => ({
  ...c,
  origin,
  source: source as z.infer<typeof CardInput>['source'],
  due: due.toISOString(),
  createdAt: createdAt.toISOString(),
});

type Input = z.infer<typeof CardInput> & { attemptId?: string };
const cardKey = (c: { source: string; front: string; back: string }) => JSON.stringify([c.source, c.front, c.back]);

/** The card the web's "Add top fixes to review deck" creates for a fix (trimmed like CardInput). */
export const fixCard = (f: Fix): Input => ({ front: `${f.title}\n\n${f.before}`.trim(), back: `${f.after}\n\n${f.why}`.trim(), source: 'fix' });

/** The user's cards identical (source, front, back) to any of the inputs, by cardKey. */
async function existing(userId: string, input: Input[]) {
  if (!input.length) return new Map<string, CardRow>();
  const rows = await db.select().from(cards).where(and(eq(cards.userId, userId), inArray(cards.front, [...new Set(input.map((c) => c.front))])));
  return new Map(rows.map((r) => [cardKey(r), r]));
}

/** Per input: is an identical card already in the user's deck? */
export async function inDeck(userId: string, input: Input[]) {
  const have = await existing(userId, input);
  return input.map((c) => have.has(cardKey(c)));
}

/** Adds cards, skipping ones already in the deck (returns the existing card instead), so repeated "Add" clicks never duplicate.
 *  ponytail: check-then-insert; two truly concurrent adds can still race — a unique index on (user_id, source, md5(front), md5(back)) closes it. */
export async function insertCards(userId: string, input: Input[]) {
  const have = await existing(userId, input);
  const fresh = [...new Map(input.filter((c) => !have.has(cardKey(c))).map((c) => [cardKey(c), { ...c, userId }])).values()];
  if (fresh.length) for (const r of await db.insert(cards).values(fresh).returning()) have.set(cardKey(r), r);
  return input.map((c) => toCard(have.get(cardKey(c))!));
}

export function register(app: App) {
  app.openapi(
    createRoute({
      ...authed,
      method: 'post',
      path: '/api/cards',
      summary: 'Add a card to the review deck',
      request: { body: { required: true, content: { 'application/json': { schema: CardInput } } } },
      responses: { 201: json(CardSchema, 'Created') },
    }),
    async (c) => c.json((await insertCards(currentUser(c).id, [c.req.valid('json')]))[0]!, 201),
  );

  app.openapi(
    createRoute({
      ...authed,
      method: 'post',
      path: '/api/cards/bulk',
      summary: 'Add several cards at once (e.g. all top fixes of a result)',
      request: { body: { required: true, content: { 'application/json': { schema: z.object({ cards: z.array(CardInput).min(1).max(50) }) } } } },
      responses: { 201: json(z.object({ cards: z.array(CardSchema) }), 'Created') },
    }),
    async (c) => c.json({ cards: await insertCards(currentUser(c).id, c.req.valid('json').cards) }, 201),
  );

  app.openapi(
    createRoute({
      ...authed,
      method: 'get',
      path: '/api/cards/due',
      summary: 'Cards due for review now (oldest due first, max 50) and the total due count; optionally one category, or ahead of schedule',
      request: {
        query: z.object({
          category: z.string().max(60).optional(),
          practice: z.enum(['1']).optional().openapi({ description: '1 = include cards not yet due (soonest first): a drill on demand' }),
        }),
      },
      responses: { 200: json(z.object({ cards: z.array(CardSchema), total: z.number(), deck: z.number().openapi({ description: 'Total cards owned' }) }).openapi('DueCards'), 'Due cards') },
    }),
    async (c) => {
      const { category, practice } = c.req.valid('query');
      const live = and(eq(cards.userId, currentUser(c).id), eq(cards.suspended, false));
      const where = and(live, category ? eq(cards.category, category) : undefined, practice ? undefined : lte(cards.due, new Date()));
      const [rows, [{ total } = { total: 0 }], [{ deck } = { deck: 0 }]] = await Promise.all([
        db
          .select({ card: cards, skill: attempts.skill, part: attempts.part, title: prompts.title, at: attempts.createdAt })
          .from(cards)
          .leftJoin(attempts, eq(attempts.id, cards.attemptId))
          .leftJoin(prompts, eq(prompts.id, attempts.promptId))
          .where(where)
          .orderBy(asc(cards.due))
          .limit(50),
        db.select({ total: count() }).from(cards).where(where),
        db.select({ deck: count() }).from(cards).where(live),
      ]);
      const out = rows.map(({ card, skill, part, title, at }) => toCard(card, skill && part != null && title && at ? { skill, part, title, at: at.toISOString() } : null));
      return c.json({ cards: out, total, deck }, 200);
    },
  );

  app.openapi(
    createRoute({
      ...authed,
      method: 'post',
      path: '/api/cards/{id}/review',
      summary: 'Grade a card (SM-2: 0-2 again, 3 hard, 4 good, 5 easy) and reschedule it',
      request: {
        params: z.object({ id: z.string().openapi({ param: { name: 'id', in: 'path' } }) }),
        body: { required: true, content: { 'application/json': { schema: z.object({ grade: z.number().int().min(0).max(5) }).openapi('CardReview') } } },
      },
      responses: { 200: json(CardSchema, 'Rescheduled card'), 404: json(ErrorSchema, 'Not found') },
    }),
    async (c) => {
      const where = and(eq(cards.id, c.req.valid('param').id), eq(cards.userId, currentUser(c).id));
      const [card] = await db.select().from(cards).where(where);
      if (!card) return c.json({ error: 'Card not found' }, 404);
      const next = review(card, c.req.valid('json').grade as 0 | 1 | 2 | 3 | 4 | 5);
      const [updated] = await db.update(cards).set(next).where(where).returning();
      await recordActivity(card.userId, { drills: 1 });
      return c.json(toCard(updated!), 200);
    },
  );

  app.openapi(
    createRoute({
      ...authed,
      method: 'post',
      path: '/api/cards/{id}/skip',
      summary: 'Skip a card for good: it is never due again (it stays out of drills and counts)',
      request: { params: z.object({ id: z.string().openapi({ param: { name: 'id', in: 'path' } }) }) },
      responses: { 200: json(z.object({ ok: z.boolean() }), 'Skipped'), 404: json(ErrorSchema, 'Not found') },
    }),
    async (c) => {
      const [gone] = await db.update(cards).set({ suspended: true }).where(and(eq(cards.id, c.req.valid('param').id), eq(cards.userId, currentUser(c).id))).returning({ id: cards.id });
      if (!gone) return c.json({ error: 'Card not found' }, 404);
      return c.json({ ok: true }, 200);
    },
  );
}
