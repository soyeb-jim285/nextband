// Round C planner (docs/engagement/notifications-and-streaks.md §4, §5): every 15 minutes, for each student with a device whose send window
// (§5.2) is now, build today's candidates, apply caps, back-off and quiet hours, pick one type (§5.3) and one template (§5.5 Thompson
// sampling), and queue it for pushSweep. One nudge per student per local day: the row's dedupe key is nudge:<user>:<day>, checked under
// an advisory lock, so reruns and several processes queue it once. The 10% holdout gets a `holdout` row instead (never sent), so its
// practice-within-2-h rate can be compared with the students who got the push.
import { checkPush, fillTemplate } from '@ielts/core';
import { and, desc, eq, gt, inArray, isNotNull, lt, lte, sql } from 'drizzle-orm';
import { db } from '../db/client';
import { activityDays, cards, mockExams, notificationDevices, notificationLog } from '../db/schema';
import { languageOf, pushEnabled } from '../push';
import { mergeSettings, type Settings } from '../settings';
import { addDays, getStreak, MILESTONES, startOfDay } from '../streaks';
import {
  allowedAt, backoffLevel, fmtMin, inWindow, isHoldout, localClock, NUDGE_SWITCH, NUDGE_TYPES, pickCandidate, pickTemplate, quietOf, sendMinute,
  underCaps, zoneIn, type Candidate, type NudgeType, type Rng, type Stats, type TypeHistory,
} from './rules';
import { NUDGE_TEMPLATES } from './templates';

const DAY = 864e5;
const ACT_MS = 2 * 3_600_000; // §5.7: practised within 2 h of the send
const EXAM_DAYS = [30, 21, 14, 7, 3, 1];
const WINBACK_DAYS = [3, 7, 14];
const SKILL_LABEL = { listening: 'Listening', reading: 'Reading', writing: 'Writing', speaking: 'Speaking' } as const;
type Skill = keyof typeof SKILL_LABEL;
/** Mistake categories (ai/schemas.ts) as push words; grammar.other is too vague to alert on. */
const PATTERN_LABEL: Record<string, string> = {
  'grammar.article': 'Articles', 'grammar.tense': 'Tenses', 'grammar.agreement': 'Subject-verb agreement', 'grammar.preposition': 'Prepositions',
  'grammar.word-order': 'Word order', 'grammar.plural': 'Plurals', 'grammar.sentence-structure': 'Sentence structure', 'grammar.punctuation': 'Punctuation',
  'lexis.collocation': 'Collocations', 'lexis.word-choice': 'Word choice', 'lexis.word-form': 'Word forms', 'lexis.spelling': 'Spelling',
  'lexis.repetition': 'Repeated words', 'cohesion.overuse': 'Overused linkers', 'cohesion.missing': 'Missing linkers', 'cohesion.reference': 'Reference words',
  'task.relevance': 'Staying on topic', 'task.overview': 'Task 1 overviews', 'task.position': 'A clear position', 'pronunciation.word': 'Pronunciation',
  'fluency.hesitation': 'Hesitation',
};

const n = (x: unknown) => Number(x ?? 0);

// ---------- §4 candidates ----------
/** Every P1/P2 type that applies to the student right now, with its template variables and deep link. */
export async function candidates(userId: string, s: Settings, tz: string, now: Date): Promise<Candidate[]> {
  const out: Candidate[] = [];
  const today = localClock(now, tz).day;
  const daysBetween = (a: string, b: string) => Math.round((Date.parse(b) - Date.parse(a)) / DAY);

  // #3 streak saver / #9 milestone near
  const st = await getStreak(userId, now);
  if (st.today === 'pending' && st.current >= 2) out.push({ type: 'streak', vars: { streak: String(st.current) }, route: '/streak' });
  if (st.today === 'done' && MILESTONES.includes(st.current + 1)) out.push({ type: 'milestone', vars: { streak: String(st.current), milestone: String(st.current + 1) }, route: '/streak' });

  // #5 drills due: 5+ cards due and no drill yesterday
  const [[due], [yday]] = await Promise.all([
    db.select({ n: sql<number>`count(*)::int` }).from(cards).where(and(eq(cards.userId, userId), eq(cards.suspended, false), lte(cards.due, now))),
    db.select({ drills: activityDays.drills }).from(activityDays).where(and(eq(activityDays.userId, userId), eq(activityDays.day, addDays(today, -1)))),
  ]);
  if (n(due?.n) >= 5 && !yday?.drills) out.push({ type: 'drills', vars: { due: String(due!.n) }, route: '/improve/drill' });

  // #6 pattern alert: one mistake category in 3 of the last 4 marked tests of a skill, the latest of them this week.
  // ponytail: a Speaking part counts as a test (the mistakes table has Writing and Speaking only; L/R patterns wait for round D's facts packet)
  const [pat] = (await db.execute(sql`with t as (
      select a.id, a.skill, a.created_at, row_number() over (partition by a.skill order by a.created_at desc) rn
      from attempts a join analyses x on x.attempt_id = a.id where a.user_id = ${userId} and a.status = 'done' and x.overall > 0)
    select t.skill, m.category, count(distinct t.id)::int as tests from t join mistakes m on m.attempt_id = t.id
    where t.rn <= 4 and m.category <> 'grammar.other' group by t.skill, m.category
    having count(distinct t.id) >= 3 and max(t.created_at) > ${new Date(now.getTime() - 7 * DAY).toISOString()}
    order by tests desc, max(t.created_at) desc limit 1`)) as unknown as { skill: Skill; category: string; tests: number }[];
  if (pat && PATTERN_LABEL[pat.category])
    out.push({ type: 'pattern', vars: { pattern: PATTERN_LABEL[pat.category]!, skill: SKILL_LABEL[pat.skill], count: String(pat.tests), of: '4' }, route: `/improve/drill?pattern=${encodeURIComponent(pat.category)}` });

  // #7 exam countdown at 30, 21, 14, 7, 3 and 1 days
  const toExam = s.examDate ? daysBetween(today, s.examDate) : null;
  if (toExam != null && EXAM_DAYS.includes(toExam)) out.push({ type: 'exam', vars: { days: String(toExam) }, route: '/mock' });

  // #8 skill gone cold (7–30 days untouched while another skill was practised this week) and #12 win-back (3 / 7 / 14 days with nothing)
  const last = (await db.execute(sql`select skill, max(at) as at, (array_agg(band order by at desc))[1] as band from (
      select a.skill::text, a.created_at as at, x.overall as band from attempts a left join analyses x on x.attempt_id = a.id where a.user_id = ${userId} and a.status = 'done'
      union all select t.skill::text, l.submitted_at, l.band from lr_attempts l join lr_tests t on t.id = l.test_id where l.user_id = ${userId} and l.status = 'submitted'
    ) s group by skill`)) as unknown as { skill: Skill; at: Date | string; band: string | number | null }[];
  const ago = (r: { at: Date | string }) => (now.getTime() - new Date(r.at).getTime()) / DAY;
  if (last.some((r) => ago(r) < 7)) {
    const cold = last.filter((r) => ago(r) >= 7 && ago(r) <= 30).sort((a, b) => ago(a) - ago(b))[0];
    if (cold) out.push({ type: 'cold', vars: { skill: SKILL_LABEL[cold.skill], days: String(Math.floor(ago(cold))), ...(cold.band != null && { band: n(cold.band).toFixed(1) }) }, route: `/${cold.skill}` });
  }
  const [act] = await db.select({ day: sql<string | null>`max(${activityDays.day})` }).from(activityDays).where(eq(activityDays.userId, userId));
  const idle = act?.day ? daysBetween(act.day, today) : null;
  if (idle != null && WINBACK_DAYS.includes(idle)) {
    const best = last.filter((r) => r.band != null).map((r) => n(r.band)).sort((a, b) => b - a)[0];
    out.push({ type: 'winback', vars: { days: String(idle), ...(best != null && { band: best.toFixed(1) }) }, route: '/' });
  }

  // #14 mock ready: started over 24 h ago, not finished, not expired
  const [mock] = await db.select().from(mockExams)
    .where(and(eq(mockExams.userId, userId), eq(mockExams.status, 'in_progress'), lt(mockExams.startedAt, new Date(now.getTime() - DAY)), gt(mockExams.expiresAt, now)))
    .orderBy(desc(mockExams.startedAt)).limit(1);
  if (mock) {
    // ponytail: "not started" per section from the mock row; a section started and abandoned reads as done here
    const left = [!mock.listeningAttemptId && 'Listening', !mock.readingAttemptId && 'Reading', !mock.writingAttemptIds.length && !mock.writingStartedAt && 'Writing', !mock.speakingSessionId && 'Speaking'].filter((x): x is string => !!x);
    if (left.length) out.push({ type: 'mock', vars: { left: left.length > 1 ? `${left.slice(0, -1).join(', ')} and ${left.at(-1)}` : left[0]! }, route: `/mock/${mock.id}` });
  }
  return out;
}

// ---------- §5.5 bandit counts ----------
/** Sends and wins per template, from the log (the stored counts): sent, not holdout, and old enough (2 h) for the outcome to be known. */
export async function templateStats(now = new Date()): Promise<Map<string, Stats>> {
  const rows = await db
    .select({ id: notificationLog.templateId, sends: sql<number>`count(*)::int`, wins: sql<number>`count(${notificationLog.actedAt})::int` })
    .from(notificationLog)
    .where(and(eq(notificationLog.status, 'sent'), eq(notificationLog.holdout, false), isNotNull(notificationLog.templateId), inArray(notificationLog.type, [...NUDGE_TYPES]), lt(notificationLog.sentAt, new Date(now.getTime() - ACT_MS))))
    .groupBy(notificationLog.templateId);
  return new Map(rows.map((r) => [r.id!, { sends: n(r.sends), wins: n(r.wins) }]));
}

// ---------- one student ----------
/** Plans today's nudge for one student whose window is now. Returns what it did. */
export async function planUser(userId: string, s: Settings, tz: string, now: Date, stats: Map<string, Stats>, rng: Rng = Math.random): Promise<'queued' | 'holdout' | null> {
  const clock = localClock(now, tz);
  const key = `nudge:${userId}:${clock.day}`;
  return db.transaction(async (tx) => {
    await tx.execute(sql`select pg_advisory_xact_lock(hashtext(${key}))`);
    if ((await tx.select({ id: notificationLog.id }).from(notificationLog).where(eq(notificationLog.dedupeKey, key)).limit(1)).length) return null;
    const holdout = isHoldout(userId);

    // nudges of the last 60 days that count: for the holdout its holdout rows (what it would have got), otherwise the sent and queued ones
    const rows = (await tx.select().from(notificationLog).where(and(eq(notificationLog.userId, userId), inArray(notificationLog.type, [...NUDGE_TYPES]), gt(notificationLog.createdAt, new Date(now.getTime() - 60 * DAY)))))
      .filter((r) => (holdout ? r.holdout : !r.holdout && (r.status === 'sent' || r.status === 'pending')));
    const at = (r: (typeof rows)[number]) => r.sentAt ?? r.scheduledFor;

    // §5.4: opening a push or the app (every app start refreshes its device row) or practising resets the back-off
    const [reset] = (await tx.execute(sql`select
        (select max(opened_at) from notification_log where user_id = ${userId}) as opened,
        (select max(last_seen_at) from notification_devices where user_id = ${userId}) as seen,
        (select max(day)::text from activity_days where user_id = ${userId}) as day`)) as unknown as { opened: Date | string | null; seen: Date | string | null; day: string | null }[];
    const resetAt = Math.max(reset?.opened ? +new Date(reset.opened) : 0, reset?.seen ? +new Date(reset.seen) : 0, reset?.day ? +startOfDay(reset.day, tz) : 0);
    const since = rows.filter((r) => +at(r) > resetAt);
    // ponytail: the holdout never opens anything, so it stays at level 0 (its volume mirrors a student who opens)
    const level = holdout ? 0 : backoffLevel(since.length);
    if (!underCaps(level, clock.day, rows.filter((r) => +at(r) > now.getTime() - 7 * DAY).map((r) => localClock(at(r), tz).day))) return null;

    let pick: Candidate | null;
    if (level === 3 && !since.some((r) => r.type === 'goodbye')) pick = s[NUDGE_SWITCH.goodbye] ? { type: 'goodbye', vars: {}, route: '/' } : null;
    else {
      const recent = rows.filter((r) => +at(r) > now.getTime() - 14 * DAY);
      const history: Partial<Record<NudgeType, TypeHistory>> = {};
      for (const r of recent) {
        const h = (history[r.type as NudgeType] ??= { sent: 0, ignored: 0, lastSentDaysAgo: null });
        h.sent++;
        if (!holdout && !r.openedAt) h.ignored++;
        const d = Math.round((Date.parse(clock.day) - Date.parse(localClock(at(r), tz).day)) / DAY);
        h.lastSentDaysAgo = h.lastSentDaysAgo == null ? d : Math.min(h.lastSentDaysAgo, d);
      }
      const all = await candidates(userId, s, tz, now);
      pick = pickCandidate(all.filter((c) => s[NUDGE_SWITCH[c.type]] && allowedAt(level, c.type)), history);
    }
    if (!pick) return null;
    if (holdout) {
      await tx.insert(notificationLog).values({ userId, type: pick.type, dedupeKey: key, status: 'skipped', holdout: true, route: pick.route, scheduledFor: now });
      return 'holdout';
    }

    const [device] = await tx.select({ locale: notificationDevices.locale }).from(notificationDevices).where(eq(notificationDevices.userId, userId)).orderBy(desc(notificationDevices.lastSeenAt)).limit(1);
    const fill = (t: { id: string; title: string; body: string }) => ({ ...t, title: fillTemplate(t.title, pick.vars), body: fillTemplate(t.body, pick.vars) });
    // templates whose variables this student has (a guest has no name, a skill may have no band yet) and whose copy passes checkPush
    const ok = NUDGE_TEMPLATES[pick.type][languageOf(s.notifyLanguage, device?.locale)].map(fill).filter((t) => !checkPush(t).length);
    if (!ok.length) {
      console.error('[push] no sendable template', pick.type, Object.keys(pick.vars));
      return null;
    }
    const t = pickTemplate(ok, stats, rng);
    await tx.insert(notificationLog).values({ userId, type: pick.type, templateId: t.id, title: t.title, body: t.body, route: pick.route, dedupeKey: key, scheduledFor: now });
    return 'queued';
  });
}

// ---------- §5.2 send time ----------
type SendRow = { id: string; data: unknown; current: number | null; last_day: string | null; starts: (number | string)[] };
/** Students with a live device (or just `userId`), their settings and cached streak, and when their tests started in the last 21 days. */
const sendRows = (now: Date, userId?: string) => {
  const since = new Date(now.getTime() - 21 * DAY).toISOString();
  return db.execute(sql`select u.id, s.data, st.current, st.last_day::text as last_day,
      array(select extract(epoch from created_at)::float8 from attempts where user_id = u.id and created_at > ${since}
            union all select extract(epoch from started_at)::float8 from lr_attempts where user_id = u.id and started_at > ${since}) as starts
    from ${userId ? sql`(select ${userId}::text as id)` : sql`(select distinct user_id as id from notification_devices where disabled_at is null)`} u
    left join user_settings s on s.user_id = u.id left join streaks st on st.user_id = u.id`) as unknown as Promise<SendRow[]>;
};
function timing(u: SendRow, now: Date) {
  const tz = zoneIn(u.data);
  const s = mergeSettings({ ...(u.data as object | null), timeZone: tz });
  const c = localClock(now, tz);
  const starts = u.starts.map((t) => localClock(new Date(Number(t) * 1000), tz).minute);
  // the cached streak row: 2+ days and not practised today (a stale row only moves the time earlier, never skips a nudge)
  const send = sendMinute({ remindAt: s.remindAt, starts, atRisk: (u.current ?? 0) >= 2 && u.last_day !== c.day, weekday: c.weekday, quiet: quietOf(s) });
  return { s, tz, c, send };
}
/** GET /api/streak `remindAt`: today's send time as HH:MM local, for the app's local streak reminder (§6); null = none fits today. */
export async function remindAtFor(userId: string, now = new Date()): Promise<string | null> {
  const [u] = await sendRows(now, userId);
  const send = u ? timing(u, now).send : null;
  return send == null ? null : fmtMin(send);
}

// ---------- the sweep ----------
let planning: Promise<number> | undefined;
/** Every 15 min (index.ts): plans the students whose send window (§5.2) is now. A no-op while push is off. */
export function nudgeSweep(now = new Date(), rng: Rng = Math.random): Promise<number> {
  return (planning ??= (async () => {
    if (!pushEnabled()) return 0;
    let queued = 0;
    try {
      const users = await sendRows(now);
      let stats: Map<string, Stats> | undefined;
      for (const u of users) {
        try {
          const { s, tz, c, send } = timing(u, now);
          if (send == null || !inWindow(c.minute, send)) continue;
          if ((await planUser(u.id, s, tz, now, (stats ??= await templateStats(now)), rng)) === 'queued') queued++;
        } catch (e) {
          console.error('[push] plan failed', u.id, e);
        }
      }
    } catch (e) {
      console.error('[push] nudge sweep failed', e);
    } finally {
      planning = undefined;
    }
    return queued;
  })());
}
