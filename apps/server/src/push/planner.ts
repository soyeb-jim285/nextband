// Round C planner (docs/engagement/notifications-and-streaks.md §4, §5): every 15 minutes, for each student with a device whose send window
// (§5.2) is now, build today's candidates, apply caps, back-off and quiet hours, pick one type (§5.3) and one template (§5.5 Thompson
// sampling), and queue it for pushSweep. One nudge per student per local day: the row's dedupe key is nudge:<user>:<day>, checked under
// an advisory lock, so reruns and several processes queue it once. The 10% holdout gets a `holdout` row instead (never sent), so its
// practice-within-2-h rate can be compared with the students who got the push.
import { checkPush, fillTemplate } from '@ielts/core';
import { and, desc, eq, gt, inArray, isNotNull, isNull, lt, lte, sql } from 'drizzle-orm';
import { db } from '../db/client';
import { activityDays, cards, mockExams, notificationDevices, notificationLog } from '../db/schema';
import { languageOf, pushEnabled } from '../push';
import { mergeSettings, type Settings } from '../settings';
import { addDays, getStreak, MILESTONES } from '../streaks';
import {
  allowedAt, backoffLevel, fmtMin, inWindow, isHoldout, localClock, NUDGE_SWITCH, NUDGE_TYPES, pickCandidate, pickTemplate, quietOf, sendMinute,
  underCaps, zoneIn, type Candidate, type NudgeType, type Rng, type Stats, type TypeHistory,
} from './rules';
import { fits, nudgeTemplates } from './templates';

const DAY = 864e5;
const ACT_MS = 2 * 3_600_000; // §5.7: practised within 2 h of the send
const EXAM_DAYS = [30, 21, 14, 7, 3, 1];
const WINBACK_DAYS = [3, 7, 14];
const SKILL_LABEL = { listening: 'Listening', reading: 'Reading', writing: 'Writing', speaking: 'Speaking' } as const;
type Skill = keyof typeof SKILL_LABEL;
/** Mistake categories (ai/schemas.ts) as push words, each naming the mistake (the copy says it "came up" or "tripped you up");
 *  grammar.other is too vague to alert on. */
export const PATTERN_LABEL: Record<string, string> = {
  'grammar.article': 'Article mistakes', 'grammar.tense': 'Tense mistakes', 'grammar.agreement': 'Subject-verb errors',
  'grammar.preposition': 'Preposition mistakes', 'grammar.word-order': 'Word order mistakes', 'grammar.plural': 'Plural mistakes',
  'grammar.sentence-structure': 'Broken sentence structure', 'grammar.punctuation': 'Punctuation mistakes', 'lexis.collocation': 'Collocation mistakes',
  'lexis.word-choice': 'Wrong word choices', 'lexis.word-form': 'Word form mistakes', 'lexis.spelling': 'Spelling mistakes', 'lexis.repetition': 'Repeated words',
  'cohesion.overuse': 'Overused linkers', 'cohesion.missing': 'Missing linkers', 'cohesion.reference': 'Unclear reference words',
  'task.relevance': 'Going off topic', 'task.overview': 'Missing Task 1 overviews', 'task.position': 'An unclear position',
  'pronunciation.word': 'Mispronounced words', 'fluency.hesitation': 'Long hesitations',
};

const n = (x: unknown) => Number(x ?? 0);

// ---------- §4 candidates ----------
/** Every P1/P2 type that applies to the student right now, with its template variables and deep link. */
export async function candidates(userId: string, s: Settings, tz: string, now: Date): Promise<Candidate[]> {
  const out: Candidate[] = [];
  const today = localClock(now, tz).day;
  const daysBetween = (a: string, b: string) => Math.round((Date.parse(b) - Date.parse(a)) / DAY);

  // #3 streak saver, or #9 milestone near merged into it: not practised today and the milestone is two practice days away (today + tomorrow).
  // A saved freeze covers a missed day, so the streak doesn't really end tonight: no FOMO copy then (§2 rule 5).
  const st = await getStreak(userId, now);
  const noFomo = st.freezes > 0 ? { noFomo: true } : {};
  if (st.today === 'pending' && st.current >= 1 && MILESTONES.includes(st.current + 2)) out.push({ type: 'milestone', vars: { milestone: String(st.current + 2) }, route: '/streak', ...noFomo });
  else if (st.today === 'pending' && st.current >= 2) out.push({ type: 'streak', vars: { streak: String(st.current) }, route: '/streak', ...noFomo });

  // #5 drills due: 5+ cards due and no drill yesterday
  const [[due], [yday], [deck]] = await Promise.all([
    db.select({ n: sql<number>`count(*)::int` }).from(cards).where(and(eq(cards.userId, userId), eq(cards.suspended, false), lte(cards.due, now))),
    db.select({ drills: activityDays.drills }).from(activityDays).where(and(eq(activityDays.userId, userId), eq(activityDays.day, addDays(today, -1)))),
    db.select({ n: sql<number>`count(*)::int` }).from(cards).where(and(eq(cards.userId, userId), eq(cards.suspended, false))),
  ]);
  if (n(due?.n) >= 5 && !yday?.drills) out.push({ type: 'drills', vars: { due: String(due!.n) }, route: '/improve/drill' });

  // #6 pattern alert: one mistake category in 3 of the last 4 marked tests of a skill (`of` = how many there are, at most 4), the latest this week.
  // ponytail: a Speaking part counts as a test (the mistakes table has Writing and Speaking only; L/R patterns wait for round D's facts packet)
  const [pat] = (await db.execute(sql`with t as (
      select a.id, a.skill, a.created_at, row_number() over (partition by a.skill order by a.created_at desc) rn, count(*) over (partition by a.skill) total
      from attempts a join analyses x on x.attempt_id = a.id where a.user_id = ${userId} and a.status = 'done' and x.overall > 0)
    select t.skill, m.category, count(distinct t.id)::int as tests, least(max(t.total), 4)::int as of from t join mistakes m on m.attempt_id = t.id
    where t.rn <= 4 and m.category <> 'grammar.other' group by t.skill, m.category
    having count(distinct t.id) >= 3 and max(t.created_at) > ${new Date(now.getTime() - 7 * DAY).toISOString()}
    order by tests desc, max(t.created_at) desc limit 1`)) as unknown as { skill: Skill; category: string; tests: number; of: number }[];
  if (pat && PATTERN_LABEL[pat.category]) {
    // the alert opens that mistake's drills: none to drill, no alert; fewer than 5, no copy promising "5 drills" or "drills are ready"
    const [mine] = await db.select({ n: sql<number>`count(*)::int` }).from(cards).where(and(eq(cards.userId, userId), eq(cards.suspended, false), eq(cards.category, pat.category)));
    if (n(mine?.n) > 0)
      out.push({ type: 'pattern', vars: { pattern: PATTERN_LABEL[pat.category]!, skill: SKILL_LABEL[pat.skill], hits: String(pat.tests), of: String(pat.of) }, route: `/improve/drill?pattern=${encodeURIComponent(pat.category)}`, ...(n(mine?.n) < 5 && { noDrill: true }) });
  }

  // #8 skill gone cold (7–30 days untouched while another skill was practised this week) and #12 win-back (3 / 7 / 14 days with nothing)
  const last = (await db.execute(sql`select skill, max(at) as at, (array_agg(band order by at desc))[1] as band from (
      select a.skill::text, a.created_at as at, x.overall as band from attempts a left join analyses x on x.attempt_id = a.id where a.user_id = ${userId} and a.status = 'done'
      union all select t.skill::text, l.submitted_at, l.band from lr_attempts l join lr_tests t on t.id = l.test_id where l.user_id = ${userId} and l.status = 'submitted'
    ) s group by skill`)) as unknown as { skill: Skill; at: Date | string; band: string | number | null }[];
  const ago = (r: { at: Date | string }) => (now.getTime() - new Date(r.at).getTime()) / DAY;
  const banded = last.filter((r) => n(r.band) > 0);
  const latest = [...banded].sort((a, b) => ago(a) - ago(b))[0]; // the band of the most recent marked test ({band} of exam and win-back)

  // #7 exam countdown at 30, 21, 14, 7, 3 and 1 days, with the last band and (with 2+ banded skills) the weakest skill
  const toExam = s.examDate ? daysBetween(today, s.examDate) : null;
  if (toExam != null && EXAM_DAYS.includes(toExam)) {
    const [low, next] = [...banded].sort((a, b) => n(a.band) - n(b.band));
    const weakest = low && next && n(low.band) < n(next.band) ? low : undefined; // a tie has no weakest
    out.push({ type: 'exam', vars: { days: String(toExam), ...(latest && { band: n(latest.band).toFixed(1) }), ...(weakest && { weakest: SKILL_LABEL[weakest.skill] }) }, route: '/mock' });
  }
  if (last.some((r) => ago(r) < 7)) {
    const cold = last.filter((r) => ago(r) >= 7 && ago(r) <= 30).sort((a, b) => ago(a) - ago(b))[0];
    if (cold) out.push({ type: 'cold', vars: { skill: SKILL_LABEL[cold.skill], idle: String(Math.floor(ago(cold))), ...(n(cold.band) > 0 && { band: n(cold.band).toFixed(1) }) }, route: `/${cold.skill}` });
  }
  const [act] = await db.select({ day: sql<string | null>`max(${activityDays.day})` }).from(activityDays).where(eq(activityDays.userId, userId));
  const idle = act?.day ? daysBetween(act.day, today) : null;
  if (idle != null && WINBACK_DAYS.includes(idle)) {
    out.push({ type: 'winback', vars: { idle: String(idle), ...(latest && { band: n(latest.band).toFixed(1) }) }, route: '/' });
  }

  // #14 mock ready: started over 24 h ago, not finished, not expired
  const [mock] = await db.select().from(mockExams)
    .where(and(eq(mockExams.userId, userId), eq(mockExams.status, 'in_progress'), lt(mockExams.startedAt, new Date(now.getTime() - DAY)), gt(mockExams.expiresAt, now)))
    .orderBy(desc(mockExams.startedAt)).limit(1);
  if (mock) {
    // ponytail: "not started" per section from the mock row; a section started and abandoned reads as done here
    const left = [!mock.listeningAttemptId && 'Listening', !mock.readingAttemptId && 'Reading', !mock.writingAttemptIds.length && !mock.writingStartedAt && 'Writing', !mock.speakingSessionId && 'Speaking'].filter((x): x is string => !!x);
    // a mock with no section begun has no saved progress, which most of the copy claims
    if (left.length && left.length < 4) out.push({ type: 'mock', vars: { parts: left.length > 1 ? `${left.slice(0, -1).join(', ')} and ${left.at(-1)}` : left[0]! }, route: `/mock/${mock.id}` });
  }
  // fewer than 5 cards: drill-only copy ("5 drill cards keep it") can't be followed, so only copy offering a test part (templates.ts fits)
  return n(deck?.n) >= 5 ? out : out.map((c) => ({ ...c, noDrill: true }));
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

// ---------- §5.4 back-off and §2 caps ----------
type Reader = Pick<typeof db, 'select' | 'execute'>;
/** One student's nudges of the last 60 days, back-off level and whether the caps leave room today. The holdout runs the same way over
 *  its holdout rows (never opened, as it never sees them), so both arms are throttled alike and the §5.7 comparison stays fair. */
async function nudgeState(x: Reader, userId: string, tz: string, now: Date) {
  const holdout = isHoldout(userId);
  const rows = (await x.select().from(notificationLog).where(and(eq(notificationLog.userId, userId), inArray(notificationLog.type, [...NUDGE_TYPES]), gt(notificationLog.createdAt, new Date(now.getTime() - 60 * DAY)))))
    .filter((r) => (holdout ? r.holdout : !r.holdout && (r.status === 'sent' || r.status === 'pending')));
  const at = (r: (typeof rows)[number]) => r.sentAt ?? r.scheduledFor;
  // §5.4: opening a push, opening the app (a foreground PUT /api/push/token; an FCM wake-up only refreshes last_seen_at) or practising resets it
  const [reset] = (await x.execute(sql`select
      (select max(opened_at) from notification_log where user_id = ${userId}) as opened,
      (select max(app_opened_at) from notification_devices where user_id = ${userId}) as app,
      (select practised_at from streaks where user_id = ${userId}) as practised,
      (select max(coalesce(sent_at, scheduled_for)) from notification_log where user_id = ${userId} and type = 'goodbye' and holdout = ${holdout}
        and (holdout or status in ('sent', 'pending'))) as bye`)) as unknown as Record<'opened' | 'app' | 'practised' | 'bye', Date | string | null>[];
  const resetAt = Math.max(...(['opened', 'app', 'practised'] as const).map((k) => (reset?.[k] ? +new Date(reset[k]) : 0)));
  const since = rows.filter((r) => +at(r) > resetAt);
  // the goodbye holds until a reset, however old: its unopened rows age out of the 60 days, the promise doesn't (§5.4)
  const bye = !!reset?.bye && +new Date(reset.bye) > resetAt;
  const level = bye ? 3 : backoffLevel(since.length);
  const open = underCaps(level, localClock(now, tz).day, rows.filter((r) => +at(r) > now.getTime() - 7 * DAY).map((r) => localClock(at(r), tz).day));
  return { holdout, rows, at, bye, level, open };
}

// ---------- one student ----------
/** Plans today's nudge for one student whose window is now. Returns what it did. */
export async function planUser(userId: string, s: Settings, tz: string, now: Date, stats: Map<string, Stats>, rng: Rng = Math.random): Promise<'queued' | 'holdout' | null> {
  const clock = localClock(now, tz);
  const key = `nudge:${userId}:${clock.day}`;
  const planned = (x: Reader) => x.select({ id: notificationLog.id }).from(notificationLog).where(eq(notificationLog.dedupeKey, key)).limit(1);
  // the reads that need no lock happen first, on the pool, so the locked transaction holds one connection; the key is checked again under it
  if ((await planned(db)).length) return null;
  const all = await candidates(userId, s, tz, now);
  const [device] = await db.select({ locale: notificationDevices.locale }).from(notificationDevices)
    .where(and(eq(notificationDevices.userId, userId), isNull(notificationDevices.disabledAt))).orderBy(desc(notificationDevices.lastSeenAt)).limit(1);
  return db.transaction(async (tx) => {
    await tx.execute(sql`select pg_advisory_xact_lock(hashtext(${key}))`);
    if ((await planned(tx)).length) return null;
    const { holdout, rows, at, bye, level, open } = await nudgeState(tx, userId, tz, now);
    if (!open) return null;

    let pick: Candidate | null;
    // level 3: the goodbye once (when its switch is on), then only what allowedAt(3) lets through
    if (level === 3 && s[NUDGE_SWITCH.goodbye] && !bye) pick = { type: 'goodbye', vars: {}, route: '/' };
    else {
      const recent = rows.filter((r) => +at(r) > now.getTime() - 14 * DAY);
      const history: Partial<Record<NudgeType, TypeHistory>> = {};
      for (const r of recent) {
        const h = (history[r.type as NudgeType] ??= { sent: 0, ignored: 0, lastSentDaysAgo: null });
        h.sent++;
        // fatigue: delivered (or, for the holdout, logged) over 2 h ago and never opened; a queued row isn't ignored yet
        if (!r.openedAt && (holdout || r.status === 'sent') && +at(r) < now.getTime() - ACT_MS) h.ignored++;
        const d = Math.round((Date.parse(clock.day) - Date.parse(localClock(at(r), tz).day)) / DAY);
        h.lastSentDaysAgo = h.lastSentDaysAgo == null ? d : Math.min(h.lastSentDaysAgo, d);
      }
      pick = pickCandidate(all.filter((c) => s[NUDGE_SWITCH[c.type]] && allowedAt(level, c.type)), history);
    }
    if (!pick) return null;
    if (holdout) {
      await tx.insert(notificationLog).values({ userId, type: pick.type, dedupeKey: key, status: 'skipped', holdout: true, route: pick.route, scheduledFor: now });
      return 'holdout';
    }

    const fill = (t: { id: string; title: string; body: string }) => ({ ...t, title: fillTemplate(t.title, pick.vars), body: fillTemplate(t.body, pick.vars) });
    // templates whose facts this student has (a skill may have no band yet: unfilled fails checkPush), that fit them (fits), and pass checkPush
    const ok = nudgeTemplates(pick.type, pick.vars, languageOf(s.notifyLanguage, device?.locale)).filter((t) => fits(t, pick)).map(fill).filter((t) => !checkPush(t).length);
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
  const atRisk = (u.current ?? 0) >= 2 && u.last_day !== c.day;
  const send = sendMinute({ remindAt: s.remindAt, starts, atRisk, weekday: c.weekday, quiet: quietOf(s) });
  return { s, tz, c, send, atRisk };
}
/** GET /api/streak `remindAt` (after getStreak refreshed the cached row): today's streak-saver time as HH:MM local, for the app's local
 *  fallback (§6). null when the app must not remind today: the holdout (§1), streak reminders off, no streak at risk, a back-off level
 *  that stops the streak saver (2+, §5.4) or no room in today's caps (a nudge already went today, or every-other-day at level 1). */
export async function remindAtFor(userId: string, now = new Date()): Promise<string | null> {
  if (isHoldout(userId)) return null;
  const [u] = await sendRows(now, userId);
  if (!u) return null;
  const { s, tz, send, atRisk } = timing(u, now);
  if (send == null || !atRisk || !s[NUDGE_SWITCH.streak]) return null;
  const { level, open } = await nudgeState(db, userId, tz, now);
  return open && allowedAt(level, 'streak') ? fmtMin(send) : null;
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
