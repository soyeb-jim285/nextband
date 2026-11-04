import { createRoute, z } from '@hono/zod-openapi';
import { eq } from 'drizzle-orm';
import { clearBearerCache, currentUser, isCambridgeAllowed, isOwner, purgeUserData, requireUser } from '../auth';
import { db } from '../db/client';
import { user as users } from '../db/schema';
import { ApiError } from '../errors';
import { clientIpHash } from '../ip';
import { payerOf, quotaSnapshot } from '../quota';
import { QuotaFields } from './community';
import { lrCounts } from './lr';
import { getSettings, SettingsSchema } from '../settings';
import type { App } from '../types';

const MeSchema = z
  .object({
    user: z.object({
      id: z.string(),
      email: z.string().openapi({ description: 'Empty for a guest' }),
      name: z.string(),
      emailVerified: z.boolean(),
      isAnonymous: z.boolean().openapi({ description: 'A guest (anonymous session, no account yet): show "Create an account", hide keys, history and review' }),
    }),
    settings: SettingsSchema,
    cambridgeAccess: z.boolean(),
    isOwner: z.boolean().openapi({ description: 'The site owner: shows the Admin area' }),
    ...QuotaFields,
  })
  .openapi('Me');

export function register(app: App) {
  app.openapi(
    createRoute({
      method: 'get',
      path: '/api/me',
      tags: ['Account'],
      summary: 'Current user, settings and feature access',
      security: [{ bearer: [] }],
      middleware: [requireUser] as const,
      responses: { 200: { description: 'Current user', content: { 'application/json': { schema: MeSchema } } } },
    }),
    async (c) => {
      const user = currentUser(c);
      const payer = await payerOf(user);
      return c.json(
        {
          user: { ...user, email: user.isAnonymous ? '' : user.email },
          settings: await getSettings(user.id),
          cambridgeAccess: isCambridgeAllowed(user),
          isOwner: isOwner(user),
          ...(await quotaSnapshot(payer, clientIpHash(c))),
          lr: await lrCounts(user),
        },
        200,
      );
    },
  );

  // Guests have no password, and Better Auth's delete-user wants one or a session younger than a day: this deletes a guest outright.
  app.openapi(
    createRoute({
      method: 'delete',
      path: '/api/me/guest',
      tags: ['Account'],
      summary: 'Delete the current guest and all its data (guests only)',
      security: [{ bearer: [] }],
      middleware: [requireUser] as const,
      responses: {
        200: { description: 'Deleted', content: { 'application/json': { schema: z.object({ ok: z.literal(true) }) } } },
        403: { description: 'Not a guest: accounts delete with their password' },
      },
    }),
    async (c) => {
      const me = currentUser(c);
      if (!me.isAnonymous) throw new ApiError(403, { error: 'Accounts are deleted with their password.', code: 'not_guest' });
      await purgeUserData(me.id);
      await db.delete(users).where(eq(users.id, me.id)); // cascades sessions, attempts, cards, settings
      clearBearerCache(); // the deleted guest's token must stop working at once
      return c.json({ ok: true as const }, 200);
    },
  );
}
