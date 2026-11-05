import { createRoute, z } from '@hono/zod-openapi';
import { currentUser, requireUser } from '../auth';
import { remindAtFor } from '../push/planner';
import { getStreak } from '../streaks';
import type { App } from '../types';

const Day = z.string().openapi({ description: 'YYYY-MM-DD in the student\'s time zone' });
const Level = z.number().int().min(0).max(3).openapi({ description: '0 none, 1 drills only, 2 a test, 3 a test plus drills (or 3+ tests)' });
const Streak = z
  .object({
    date: Day.openapi({ description: 'Today in the student\'s time zone (the calendar every other field uses)' }),
    current: z.number(),
    longest: z.number(),
    freezes: z.number().openapi({ description: 'Saved freezes (0-2): 1 earned per 7 days in a row; a missed day uses one' }),
    today: z.enum(['done', 'pending']),
    week: z.array(z.object({ day: Day, state: z.enum(['done', 'frozen', 'missed', 'today', 'future']) })).openapi({ description: 'Monday to Sunday of the current local week; today = not practised yet' }),
    month: z.array(z.object({ day: Day, level: Level })).openapi({ description: 'Every day of the current local month (kept for older clients; `days` covers it)' }),
    days: z.array(z.object({ day: Day, level: Level, frozen: z.boolean() })).openapi({ description: 'The last 182 local days ending today, oldest first (6-month heatmap); frozen = a freeze or repair covered this missed day' }),
    practicedLast35: z.number().int().openapi({ description: 'Practice days in the last 35 local days, today included' }),
    nextMilestone: z.number().nullable(),
    repair: z
      .object({ target: z.number(), until: z.string(), daysDone: z.number() })
      .nullable()
      .openapi({ description: 'Broken streak with no freeze: practise on 2 separate days before `until` to get `target` back' }),
    remindAt: z.string().nullable().openapi({ description: 'Today\'s reminder time, HH:MM local: the student\'s chosen time, else their usual practice time (20:30 at the latest while a streak is at risk), clear of quiet hours and Jumu\'ah. The app schedules its local streak reminder for it; null = no time fits today' }),
    justReached: z.number().optional().openapi({ description: 'A milestone (3, 7, 14, 30, 50, 100, 200, 365) reached today' }),
  })
  .openapi('Streak');

export function register(app: App) {
  app.openapi(
    createRoute({
      tags: ['Progress'],
      security: [{ bearer: [] }],
      middleware: [requireUser],
      method: 'get',
      path: '/api/streak',
      summary: 'Practice streak in the student\'s time zone: count, freezes, this week, this month, repair offer. Guests too.',
      description: 'A practice day is a submitted test part, 5 drill cards graded, or 10+ minutes of Listening/Reading. docs/engagement/notifications-and-streaks.md §3',
      responses: { 200: { description: 'Streak', content: { 'application/json': { schema: Streak } } } },
    }),
    async (c) => {
      const id = currentUser(c).id;
      const [streak, remindAt] = await Promise.all([getStreak(id), remindAtFor(id).catch(() => null)]);
      return c.json({ ...streak, remindAt }, 200);
    },
  );
}
