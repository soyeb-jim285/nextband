import { z } from 'zod';
import { eq } from 'drizzle-orm';
import { db } from './db/client';
import { userSettings } from './db/schema';

/** The analysis models: the same for everyone (no per-user choice). */
export const MODELS = {
  analysis: 'anthropic/claude-haiku-5.5', // writing: proofreading, feedback, image-figure scoring. Falls back to luna (openrouter.ts chat)
  speaking: 'anthropic/claude-haiku-5.5', // speaking feedback, scoring, disfluency tags: house Anthropic key (BYOK), falls back to luna like writing
  stt: 'elevenlabs/scribe-v2', // Scribe v2 through OpenRouter (spec §5.2), falling back to Whisper on error
};
export type Models = typeof MODELS;

/** True when the runtime knows this IANA zone. */
export const isTimeZone = (tz: string): boolean => {
  try {
    new Intl.DateTimeFormat('en', { timeZone: tz });
    return true;
  } catch {
    return false;
  }
};

const HHMM = z.string().regex(/^([01]\d|2[0-3]):[0-5]\d$/, 'HH:MM, 24-hour');

export const SettingsSchema = z.object({
  targetBand: z.number().min(4).max(9).multipleOf(0.5),
  writingAutoSubmit: z.boolean(),
  blockPaste: z.boolean(),
  productEmails: z.boolean(), // product news and practice tips by email (campaigns); account emails are always sent
  examDate: z.string().regex(/^\d{4}-\d{2}-\d{2}$/).nullable(), // YYYY-MM-DD, for the coach
  timeZone: z.string().refine(isTimeZone, 'Unknown time zone'), // IANA zone: the student's local day for the coach
  // Push notifications (docs/engagement/notifications-and-streaks.md §5.1, §6): one switch per kind, quiet hours, reminder time, language
  notifyResults: z.boolean(), // result ready, marking failed, new personal best
  notifyStreak: z.boolean(),
  notifyPractice: z.boolean(), // drills due, skill gone cold, win-back, mock not finished
  notifyExam: z.boolean(),
  notifyWeekly: z.boolean(),
  quietStart: HHMM, // local time; no nudges from quietStart to quietEnd
  quietEnd: HHMM,
  remindAt: HHMM.nullable(), // "Best time to remind me"; null = Auto (the student's usual time)
  notifyLanguage: z.enum(['en', 'banglish']).nullable(), // null = from the device locale (bn → Banglish)
});
export type Settings = z.infer<typeof SettingsSchema>;

export const SettingsPatchSchema = SettingsSchema.partial();
export type SettingsPatch = z.infer<typeof SettingsPatchSchema>;

export const DEFAULT_SETTINGS: Settings = { targetBand: 7, writingAutoSubmit: true, blockPaste: true, productEmails: true, examDate: null, timeZone: 'UTC',
  notifyResults: true, notifyStreak: true, notifyPractice: true, notifyExam: true, notifyWeekly: true, quietStart: '22:30', quietEnd: '08:00', remindAt: null, notifyLanguage: null,
};

/** Stored rows may still carry removed fields (models, liveProvider): the schema drops them. */
export const mergeSettings = (patch: SettingsPatch | Record<string, unknown> | undefined): Settings => SettingsSchema.parse({ ...DEFAULT_SETTINGS, ...patch });

export async function getSettings(userId: string): Promise<Settings> {
  const row = await db.query.userSettings.findFirst({ where: eq(userSettings.userId, userId) });
  return mergeSettings(row?.data);
}

/** The sparse stored row: `'targetBand' in` it means the student chose a target. */
export async function storedSettings(userId: string): Promise<SettingsPatch> {
  const row = await db.query.userSettings.findFirst({ where: eq(userSettings.userId, userId) });
  return SettingsPatchSchema.parse(row?.data ?? {});
}

export async function updateSettings(userId: string, patch: SettingsPatch): Promise<Settings> {
  const row = await db.query.userSettings.findFirst({ where: eq(userSettings.userId, userId) });
  const next = SettingsPatchSchema.parse({ ...(row?.data ?? {}), ...patch }); // sparse: only what the user changed
  await db.insert(userSettings).values({ userId, data: next }).onConflictDoUpdate({ target: userSettings.userId, set: { data: next } });
  return mergeSettings(next);
}
