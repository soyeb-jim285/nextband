import { createRoute } from '@hono/zod-openapi';
import { currentUser, requireUser } from '../auth';
import { ApiError } from '../errors';
import { getSettings, SettingsPatchSchema, SettingsSchema, updateSettings } from '../settings';
import type { App } from '../types';

const Settings = SettingsSchema.openapi('Settings');
const ok = { 200: { description: 'Effective settings (user overrides merged over defaults)', content: { 'application/json': { schema: Settings } } } };

export function register(app: App) {
  app.openapi(
    createRoute({
      method: 'get',
      path: '/api/settings',
      tags: ['Settings'],
      summary: 'Get settings',
      security: [{ bearer: [] }],
      middleware: [requireUser] as const,
      responses: ok,
    }),
    async (c) => c.json(await getSettings(currentUser(c).id), 200),
  );

  app.openapi(
    createRoute({
      method: 'put',
      path: '/api/settings',
      tags: ['Settings'],
      summary: 'Update settings (partial)',
      description: 'Guests may change only the push notification fields (notify*, quietStart, quietEnd, remindAt): 403 account_required otherwise.',
      security: [{ bearer: [] }],
      middleware: [requireUser] as const,
      request: { body: { required: true, content: { 'application/json': { schema: SettingsPatchSchema.openapi('SettingsPatch') } } } },
      responses: { ...ok, 403: { description: 'A guest changing a non-notification setting (account_required)' } },
    }),
    async (c) => {
      const me = currentUser(c);
      const patch = c.req.valid('json');
      // guests get result pushes too, so they can switch them off; everything else is an account's
      if (me.isAnonymous && Object.keys(patch).some((k) => !GUEST_KEYS.has(k))) throw new ApiError(403, { error: 'Create an account to use this.', code: 'account_required' });
      return c.json(await updateSettings(me.id, patch), 200);
    },
  );
}

const GUEST_KEYS = new Set(['notifyResults', 'notifyStreak', 'notifyPractice', 'notifyExam', 'notifyWeekly', 'quietStart', 'quietEnd', 'remindAt', 'notifyLanguage']);
