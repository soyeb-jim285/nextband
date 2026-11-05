import { createRoute, z } from '@hono/zod-openapi';
import { and, desc, eq, isNull, notInArray, sql } from 'drizzle-orm';
import { currentUser, requireUser } from '../auth';
import { db } from '../db/client';
import { notificationDevices, notificationLog } from '../db/schema';
import { ApiError } from '../errors';
import { pushEnabled, sendTestPush } from '../push';
import type { App } from '../types';

const MAX_DEVICES = 10; // per student; the least recently seen beyond this are dropped
const Ok = z.object({ ok: z.literal(true) });
const ok = { 200: { description: 'OK', content: { 'application/json': { schema: Ok } } } };
const common = { tags: ['Push'], security: [{ bearer: [] }], middleware: [requireUser] };
const Token = z.string().min(1).max(4096).openapi({ description: 'FCM registration token, or the Firebase Installation ID when kind is fid' });

const Register = z
  .object({
    token: Token,
    kind: z.enum(['token', 'fid']).default('token').openapi({ description: 'How FCM addresses this install: message.token (default) or message.fid' }),
    platform: z.enum(['android', 'ios', 'web']),
    appVersion: z.string().max(40).optional(),
    locale: z.string().max(35).optional().openapi({ description: 'Device locale (BCP 47, e.g. bn-BD): push language until notifyLanguage is set' }),
    foreground: z.boolean().optional().openapi({ description: 'The student opened the app (it came to the foreground). Resets the nudge back-off; a start in the background (an FCM wake-up) must not send it' }),
  })
  .openapi('PushRegister');

export function register(app: App) {
  app.openapi(
    createRoute({
      ...common,
      method: 'put',
      path: '/api/push/token',
      summary: 'Register or refresh this device for push (idempotent; call on every app start and on a new token). Guests too.',
      description: 'A token already registered to another user moves to the caller (a different account signed in on this phone). Re-registering a token FCM had rejected turns it back on.',
      request: { body: { required: true, content: { 'application/json': { schema: Register } } } },
      responses: ok,
    }),
    async (c) => {
      const me = currentUser(c);
      const b = c.req.valid('json');
      // ponytail: no proof the caller owns this install. Taking another phone's FID (not public) only redirects pushes to it, and the app drops
      // any push whose data.uid isn't its signed-in user; that phone's next launch takes its row back. A per-token move limit if that's ever abused.
      const row = { userId: me.id, kind: b.kind, platform: b.platform, appVersion: b.appVersion ?? null, locale: b.locale ?? null, lastSeenAt: new Date(), disabledAt: null, ...(b.foreground && { appOpenedAt: new Date() }) };
      await db.insert(notificationDevices).values({ token: b.token, ...row }).onConflictDoUpdate({ target: notificationDevices.token, set: row });
      const keep = db.select({ token: notificationDevices.token }).from(notificationDevices).where(eq(notificationDevices.userId, me.id)).orderBy(desc(notificationDevices.lastSeenAt)).limit(MAX_DEVICES);
      await db.delete(notificationDevices).where(and(eq(notificationDevices.userId, me.id), notInArray(notificationDevices.token, keep)));
      return c.json({ ok: true as const }, 200);
    },
  );

  app.openapi(
    createRoute({
      ...common,
      method: 'delete',
      path: '/api/push/token',
      summary: 'Forget this device (call before signing out). Only the caller\'s own token is removed; an unknown token is still 200.',
      request: { body: { required: true, content: { 'application/json': { schema: z.object({ token: Token }).openapi('PushUnregister') } } } },
      responses: ok,
    }),
    async (c) => {
      await db.delete(notificationDevices).where(and(eq(notificationDevices.token, c.req.valid('json').token), eq(notificationDevices.userId, currentUser(c).id)));
      return c.json({ ok: true as const }, 200);
    },
  );

  app.openapi(
    createRoute({
      ...common,
      method: 'post',
      path: '/api/push/opened',
      summary: 'The student opened the app from a push: `id` is the data payload\'s id. Idempotent; the first open counts.',
      request: { body: { required: true, content: { 'application/json': { schema: z.object({ id: z.string().min(1).max(64) }).openapi('PushOpened') } } } },
      responses: ok,
    }),
    async (c) => {
      await db
        .update(notificationLog)
        .set({ openedAt: sql`now()` })
        .where(and(eq(notificationLog.id, c.req.valid('json').id), eq(notificationLog.userId, currentUser(c).id), isNull(notificationLog.openedAt)));
      return c.json({ ok: true as const }, 200);
    },
  );

  app.openapi(
    createRoute({
      ...common,
      method: 'post',
      path: '/api/push/test',
      summary: 'Settings → "Send me a test notification" to every registered device of the caller (at most one every 30 s)',
      responses: {
        200: { description: 'sent: FCM accepted it for at least one device', content: { 'application/json': { schema: z.object({ sent: z.boolean() }).openapi('PushTest') } } },
        409: { description: 'No device registered (push_no_device)' },
        429: { description: 'A test was sent less than 30 s ago (push_rate_limited)' },
        503: { description: 'Push is not set up on this server (push_off)' },
      },
    }),
    async (c) => {
      const me = currentUser(c);
      if (!pushEnabled()) throw new ApiError(503, { error: 'Notifications are not available right now.', code: 'push_off' });
      const [device] = await db.select({ token: notificationDevices.token }).from(notificationDevices).where(and(eq(notificationDevices.userId, me.id), isNull(notificationDevices.disabledAt))).limit(1);
      if (!device) throw new ApiError(409, { error: 'Turn on notifications on this device first.', code: 'push_no_device' });
      const sent = await sendTestPush(me.id);
      if (sent === 'rate_limited') throw new ApiError(429, { error: 'Test sent. Try again in a few seconds.', code: 'push_rate_limited' });
      return c.json({ sent }, 200);
    },
  );
}
