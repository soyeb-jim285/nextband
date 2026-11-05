// Round C nudge copy (docs/engagement/notifications-and-streaks.md §4 #3–#9, #11–#14, §5.5). Written by coding agents, not an LLM API.
// The round B P0 copy (#1 result, #2 failed, #10 best, test) stays in notifications.ts PUSH_TEMPLATES.
//
// Tone guide (§2, §5.5):
// - Warm, specific, a little playful. Says something true about THIS student, using only the facts in NUDGE_VARS.
// - At most 1 emoji per push (title + body). Title ≤ 40 characters, body ≤ 90 (checkPush allows 110; the tone guide wants under 90).
// - FOMO only when real: a streak that ends at midnight, a real exam date. No guilt, no shame, no "your friends are ahead".
// - Never sells: no Pro, price, plan, upgrade, website, buy, link (checkPush enforces it).
// - No promises we can't keep ("this fixes it for good"). No claims about today's progress: the engine only sends when it is true.
// - Banglish is casual romanised Bangla with "tumi" (tomar, koro, dekho), English for app words (drill, streak, test, band, mock).
//
// The engine fills {vars} with fillTemplate and may only pick a template whose `vars` it has values for (e.g. a drill-only student has no {band}).
import type { PushChannel, PushLanguage } from './notifications';

export type NudgeType =
  | 'streak-saver' // #3 streak ≥ 2, not practised today, at the usual time
  | 'last-call' // #4 streak ≥ 7, still not practised, 21:30
  | 'drills-due' // #5 ≥ 5 cards due, no drill yesterday
  | 'pattern' // #6 same mistake category in 3 of the last 4 tests of one skill
  | 'exam' // #7 exam in 30, 21, 14, 7 or 3 days
  | 'exam-tomorrow' // #7 exam in 1 day (own group: "1 days" reads wrong)
  | 'cold' // #8 a skill not practised for 7+ days while others were
  | 'milestone-near' // #9 a streak milestone two practice days away (today + tomorrow), merged into #3 that day
  | 'weekly' // #11 Sunday recap; only for a week with ≥ 1 practice day (an empty week is the win-back's job)
  | 'win-back' // #12 no activity for 3 / 7 / 14 days
  | 'goodbye' // #13 back-off level 3: after this only weekly recap and exam countdown
  | 'mock-ready'; // #14 a mock started and not finished within 24 h

/** The facts the engine fills. Examples are the longest realistic values (the test fills them). */
export const NUDGE_FACTS = {
  streak: '365', // current streak in days (≥ 2)
  milestone: '365', // the next milestone: 3, 7, 14, 30, 50, 100, 200, 365
  due: '120', // drill cards due (≥ 5)
  pattern: 'Subject-verb agreement', // mistake category label, capitalised; always starts a sentence or follows ": "
  skill: 'Listening', // a skill name
  hits: '4', // tests with the pattern…
  of: '4', // …out of the last N of that skill
  days: '30', // days to the exam (≥ 3)
  band: '6.5', // last overall band (cold: last band in {skill})
  idle: '120', // days since the last practice (cold: since the last {skill} test)
  weakest: 'Listening', // lowest skill by recent band
  active: '7', // practice days this week (1–7)
  tests: '12', // test parts submitted this week
  drills: '120', // drill cards graded this week
  parts: 'Listening, Reading and Writing', // mock sections still to do
} as const;
export type NudgeVar = keyof typeof NUDGE_FACTS;

/** Which facts each type may use. */
export const NUDGE_VARS: Record<NudgeType, readonly NudgeVar[]> = {
  'streak-saver': ['streak'],
  'last-call': ['streak'],
  'drills-due': ['due'],
  pattern: ['pattern', 'skill', 'hits', 'of'],
  exam: ['days', 'weakest', 'band'],
  'exam-tomorrow': [],
  cold: ['skill', 'idle', 'band'],
  'milestone-near': ['milestone'],
  weekly: ['active', 'tests', 'drills', 'weakest'],
  'win-back': ['idle', 'band'],
  goodbye: [],
  'mock-ready': ['parts'],
};

export const NUDGE_CHANNEL: Record<NudgeType, PushChannel> = {
  'streak-saver': 'streak', 'last-call': 'streak', 'milestone-near': 'streak',
  'drills-due': 'practice', pattern: 'practice', cold: 'practice', 'win-back': 'practice', goodbye: 'practice', 'mock-ready': 'practice',
  exam: 'exam', 'exam-tomorrow': 'exam',
  weekly: 'weekly',
};

export type NudgeTone = 'fun' | 'useful' | 'fomo' | 'warm';
export type NudgeTemplate = { id: string; tone: NudgeTone; vars: readonly NudgeVar[]; title: string; body: string };

export const NUDGE_TEMPLATES: Record<NudgeType, Record<PushLanguage, NudgeTemplate[]>> = {
  'streak-saver': {
    en: [
      { id: 'saver-en-1', tone: 'fomo', vars: ['streak'], title: '🔥 {streak} days and counting', body: 'One 2-minute drill keeps it alive tonight.' },
      { id: 'saver-en-2', tone: 'useful', vars: ['streak'], title: 'Your {streak}-day streak needs one drill', body: 'Grade 5 drill cards and today counts. About 2 minutes.' },
      { id: 'saver-en-3', tone: 'fun', vars: ['streak'], title: 'Keep the {streak}-day streak rolling 🔥', body: 'A quick drill tonight does it. Two minutes, tops.' },
      { id: 'saver-en-4', tone: 'warm', vars: ['streak'], title: '{streak} days in a row. Nice.', body: "Today isn't counted yet. One short drill is enough to keep it going." },
      { id: 'saver-en-5', tone: 'fomo', vars: ['streak'], title: 'Today still counts for your streak', body: '{streak} days so far. A 2-minute drill tonight makes it one more.' },
      { id: 'saver-en-6', tone: 'useful', vars: ['streak'], title: '2 minutes keeps {streak} days going', body: 'Open your drills, grade 5 cards, done. Your streak is safe for today.' },
      { id: 'saver-en-7', tone: 'fun', vars: ['streak'], title: 'Your streak called. It wants a drill', body: '{streak} days strong. Five quick cards tonight keep it that way 🔥' },
      { id: 'saver-en-8', tone: 'useful', vars: ['streak'], title: 'Busy day? 5 cards still count', body: 'Your {streak}-day streak only needs 5 drill cards today. About 2 minutes.' },
      { id: 'saver-en-9', tone: 'useful', vars: ['streak'], title: 'Keep your {streak} days going', body: 'A test part, 5 drill cards or 10 minutes of Listening or Reading all count.' },
      { id: 'saver-en-10', tone: 'warm', vars: ['streak'], title: 'One more day for your streak?', body: "You've practised {streak} days in a row. One short drill keeps the run going." },
    ],
    banglish: [
      { id: 'saver-bn-1', tone: 'fomo', vars: ['streak'], title: '🔥 {streak} din dhore cholche', body: 'Aj ekta 2 minuter drill korlei streak beche thakbe.' },
      { id: 'saver-bn-2', tone: 'useful', vars: ['streak'], title: 'Tomar {streak} diner streak ta rakho', body: '5 ta drill card korlei aj count hobe. Matro 2 minute.' },
      { id: 'saver-bn-3', tone: 'warm', vars: ['streak'], title: 'Ajker din ta ekhono baki', body: '{streak} diner streak cholche. Ekta chhoto drill korle aj o count hobe.' },
      { id: 'saver-bn-4', tone: 'fun', vars: ['streak'], title: '{streak} din! Darun cholche 🔥', body: 'Aj ekta drill korle streak ta aro ek din barbe.' },
      { id: 'saver-bn-5', tone: 'useful', vars: ['streak'], title: 'Busy din? 5 ta card-i jothesto', body: 'Tomar {streak} diner streak er jonno aj sudhu 5 ta drill card lagbe.' },
      { id: 'saver-bn-6', tone: 'useful', vars: ['streak'], title: 'Streak ta dhore rakho', body: '{streak} din hoye geche. Ekta test part ba 5 ta drill card, jekono ekta korlei hobe.' },
    ],
  },
  'last-call': {
    en: [
      { id: 'lastcall-en-1', tone: 'fomo', vars: ['streak'], title: 'Your {streak}-day streak ends at midnight ⏳', body: "One 2-minute drill saves it. You've got this." },
      { id: 'lastcall-en-2', tone: 'fomo', vars: ['streak'], title: 'Last call for your {streak}-day streak', body: '5 drill cards before midnight and it carries on to tomorrow.' },
      { id: 'lastcall-en-3', tone: 'useful', vars: ['streak'], title: 'A few hours left on {streak} days', body: 'A quick drill tonight keeps your streak. Two minutes is enough.' },
      { id: 'lastcall-en-4', tone: 'useful', vars: ['streak'], title: 'Still time to save {streak} days', body: "Grade 5 drill cards before midnight. That's all today needs." },
      { id: 'lastcall-en-5', tone: 'warm', vars: ['streak'], title: 'Before you sleep: 2 minutes?', body: 'Your {streak}-day streak ends at midnight. One short drill keeps it going.' },
      { id: 'lastcall-en-6', tone: 'fun', vars: ['streak'], title: 'Your streak is up past bedtime ⏳', body: '{streak} days, and it ends at midnight. A 2-minute drill tucks it in.' },
      { id: 'lastcall-en-7', tone: 'useful', vars: ['streak'], title: 'One drill, then sleep', body: "Your {streak}-day streak runs out at midnight. 5 cards and it's safe." },
      { id: 'lastcall-en-8', tone: 'useful', vars: ['streak'], title: 'Quick save for {streak} days', body: 'Open drills, grade 5 cards, and your streak lives on into tomorrow.' },
      { id: 'lastcall-en-9', tone: 'warm', vars: ['streak'], title: '{streak} days is worth 2 minutes', body: 'Your streak ends at midnight. One small drill keeps all that work counting.' },
      { id: 'lastcall-en-10', tone: 'fomo', vars: ['streak'], title: 'Tonight still counts', body: 'Your {streak}-day streak needs one practice before midnight. A drill takes 2 minutes.' },
    ],
    banglish: [
      { id: 'lastcall-bn-1', tone: 'fomo', vars: ['streak'], title: '{streak} diner streak midnight e shesh ⏳', body: 'Ekta 2 minuter drill korlei beche jabe.' },
      { id: 'lastcall-bn-2', tone: 'fomo', vars: ['streak'], title: 'Shesh shujog: {streak} diner streak', body: 'Midnight er age 5 ta drill card koro, kal o cholbe.' },
      { id: 'lastcall-bn-3', tone: 'warm', vars: ['streak'], title: 'Ghumanor age 2 minute?', body: 'Tomar {streak} diner streak midnight e shesh hobe. Ekta chhoto drill korle thakbe.' },
      { id: 'lastcall-bn-4', tone: 'useful', vars: ['streak'], title: 'Ekhono shomoy ache', body: '{streak} diner streak bachate midnight er age 5 ta drill card korlei hobe.' },
      { id: 'lastcall-bn-5', tone: 'useful', vars: ['streak'], title: '{streak} din, matro 2 minuter bepar', body: 'Midnight er age ekta drill koro, streak ta cholte thakbe.' },
      { id: 'lastcall-bn-6', tone: 'fomo', vars: ['streak'], title: 'Aj rater last chance', body: 'Tomar {streak} diner streak er jonno midnight er age ekta practice lagbe.' },
    ],
  },
  'drills-due': {
    en: [
      { id: 'due-en-1', tone: 'useful', vars: ['due'], title: '{due} mistakes are ready to drill', body: 'Mistakes stick after 3 rounds of drilling. Start with a few today.' },
      { id: 'due-en-2', tone: 'useful', vars: ['due'], title: '{due} drill cards are waiting', body: "Each one is a mistake from your own tests. Fix them while they're fresh." },
      { id: 'due-en-3', tone: 'fun', vars: ['due'], title: 'Your mistakes want a rematch', body: "{due} cards are due. Beat them now and they're less likely to come back." },
      { id: 'due-en-4', tone: 'useful', vars: ['due'], title: '{due} cards due today', body: 'Spaced drills work best on time. A few minutes now saves relearning later.' },
      { id: 'due-en-5', tone: 'warm', vars: ['due'], title: 'Your drills are ready', body: '{due} cards from your own mistakes. Quick, short and all yours.' },
      { id: 'due-en-6', tone: 'useful', vars: ['due'], title: 'Time for a quick drill', body: '{due} cards are due. Even 5 of them make a difference.' },
      { id: 'due-en-7', tone: 'fun', vars: ['due'], title: '{due} cards, zero stress', body: 'Short drills built from your own mistakes. Flip, answer, done.' },
      { id: 'due-en-8', tone: 'useful', vars: ['due'], title: 'Make {due} fixes stick', body: 'Your drill cards are due. Reviewing on time is what makes them stick.' },
      { id: 'due-en-9', tone: 'fun', vars: ['due'], title: "It's drill o'clock 🧠", body: '{due} cards are ready. A short round now beats a long one later.' },
      { id: 'due-en-10', tone: 'warm', vars: ['due'], title: '{due} quick wins are waiting', body: "Every card is a mistake you've already seen once. Easy points to lock in." },
    ],
    banglish: [
      { id: 'due-bn-1', tone: 'useful', vars: ['due'], title: '{due} ta drill card ready', body: 'Tomar nijer bhul theke banano. 3 round er por mone theke jay.' },
      { id: 'due-bn-2', tone: 'fun', vars: ['due'], title: 'Tomar bhul gulo rematch chay', body: '{due} ta card due. Ekhon korle porer test e kaje lagbe.' },
      { id: 'due-bn-3', tone: 'useful', vars: ['due'], title: 'Drill er shomoy', body: '{due} ta card opekkha korche. 5 ta korleo kaj hoy.' },
      { id: 'due-bn-4', tone: 'warm', vars: ['due'], title: '{due} ta easy win opekkha korche', body: 'Shob card tomar nijer test er bhul theke. Ekhon pakka kore nao.' },
      { id: 'due-bn-5', tone: 'useful', vars: ['due'], title: 'Ajker drill ready 🧠', body: '{due} ta card due. Shomoy moto review korlei beshi mone thake.' },
      { id: 'due-bn-6', tone: 'warm', vars: ['due'], title: 'Bhul gulo thik kore felo', body: '{due} ta drill card ache. Koyek minute lagbe, kintu onek kaje lagbe.' },
    ],
  },
  pattern: {
    en: [
      { id: 'pattern-en-1', tone: 'useful', vars: ['skill', 'pattern', 'hits', 'of'], title: 'Spotted a pattern in your {skill}', body: '{pattern} came up in {hits} of your last {of} tests. 5 drills target it.' },
      { id: 'pattern-en-2', tone: 'useful', vars: ['pattern', 'hits', 'of', 'skill'], title: 'One mistake keeps coming back', body: '{pattern}: {hits} of your last {of} {skill} tests. A few drills will help.' },
      { id: 'pattern-en-3', tone: 'useful', vars: ['skill', 'pattern', 'hits', 'of'], title: 'Your next easy {skill} gain', body: '{pattern} showed up in {hits} of your last {of} tests. Fixing it helps every test.' },
      { id: 'pattern-en-4', tone: 'fun', vars: ['pattern', 'hits', 'of', 'skill'], title: 'We found your sneaky mistake 🔍', body: '{pattern}, in {hits} of your last {of} {skill} tests. 5 drills to catch it?' },
      { id: 'pattern-en-5', tone: 'useful', vars: ['skill', 'pattern', 'hits', 'of'], title: 'A quick fix for your {skill}', body: '{pattern} tripped you up in {hits} of your last {of} tests. Drill it for 2 minutes?' },
      { id: 'pattern-en-6', tone: 'useful', vars: ['hits', 'of', 'pattern', 'skill'], title: 'Same slip in {hits} of {of} tests', body: '{pattern} in your {skill}. A short drill now helps it stop repeating.' },
      { id: 'pattern-en-7', tone: 'warm', vars: ['pattern', 'hits', 'of', 'skill'], title: 'Small fix, real difference', body: '{pattern} came up in {hits} of your last {of} {skill} tests. Your drills target it.' },
      { id: 'pattern-en-8', tone: 'warm', vars: ['pattern', 'hits', 'of', 'skill'], title: "This one's worth fixing", body: "{pattern}: {hits} of your last {of} {skill} tests. It's common, and very fixable." },
      { id: 'pattern-en-9', tone: 'fun', vars: ['skill', 'pattern', 'hits', 'of'], title: 'Your {skill} has a habit', body: '{pattern} keeps showing up ({hits} of {of} tests). 5 drills to sort it out?' },
      { id: 'pattern-en-10', tone: 'warm', vars: ['pattern', 'hits', 'of', 'skill'], title: "Let's fix this one together", body: '{pattern} came up in {hits} of your last {of} {skill} tests. Drills are ready.' },
    ],
    banglish: [
      { id: 'pattern-bn-1', tone: 'useful', vars: ['skill', 'pattern', 'of', 'hits'], title: 'Tomar {skill} e ekta pattern', body: '{pattern}: shesh {of} ta test er {hits} ta te esheche. 5 ta drill try korbe?' },
      { id: 'pattern-bn-2', tone: 'useful', vars: ['pattern', 'of', 'skill', 'hits'], title: 'Ek-i bhul bar bar asche', body: '{pattern}, shesh {of} ta {skill} test er {hits} ta te. Drill e thik kore nao.' },
      { id: 'pattern-bn-3', tone: 'warm', vars: ['pattern', 'of', 'skill', 'hits'], title: 'Ekta easy fix ache', body: '{pattern}: shesh {of} ta {skill} test er {hits} ta te. Drill ready.' },
      { id: 'pattern-bn-4', tone: 'useful', vars: ['skill', 'pattern', 'hits', 'of'], title: '{skill} e ekta chhoto shomossha', body: '{pattern}: {hits}/{of} test e. 2 minuter drill korbe?' },
      { id: 'pattern-bn-5', tone: 'warm', vars: ['pattern', 'of', 'hits'], title: 'Ei bhul ta thik kora shohoj', body: '{pattern} shesh {of} ta test er {hits} ta te. Tomar jonno drill banano ache.' },
      { id: 'pattern-bn-6', tone: 'fun', vars: ['pattern', 'skill', 'hits', 'of'], title: 'Cholo eta thik kori 🔍', body: '{pattern} tomar {skill} e bar bar asche ({hits}/{of} test). 5 ta drill dekhbe?' },
    ],
  },
  exam: {
    en: [
      { id: 'exam-en-1', tone: 'useful', vars: ['days'], title: '{days} days to your IELTS', body: 'A full mock now shows where you stand, with time left to work on it.' },
      { id: 'exam-en-2', tone: 'useful', vars: ['days', 'weakest'], title: 'IELTS in {days} days 📅', body: 'Your weakest skill is {weakest}. A {weakest} test this week helps the most.' },
      { id: 'exam-en-3', tone: 'warm', vars: ['days'], title: "{days} days to go. You've got this", body: "Short daily practice beats cramming. Today's drills are ready." },
      { id: 'exam-en-4', tone: 'useful', vars: ['days'], title: '{days} days left: time for a mock', body: 'A full timed mock shows your real band and the skill to focus on next.' },
      { id: 'exam-en-5', tone: 'fomo', vars: ['days'], title: 'Exam countdown: {days} days', body: 'Every practice day from now counts. A drill or a test part today?' },
      { id: 'exam-en-6', tone: 'fun', vars: ['days'], title: '{days} sleeps until exam day', body: 'Steady wins it. A test part or a drill a day keeps you sharp.' },
      { id: 'exam-en-7', tone: 'useful', vars: ['weakest', 'days'], title: 'Focus on {weakest} for {days} days', body: "It's your lowest skill right now, so it's where practice pays off most." },
      { id: 'exam-en-8', tone: 'useful', vars: ['days'], title: '{days} days to your exam', body: 'Practise under exam timing today, so the real one feels familiar.' },
      { id: 'exam-en-9', tone: 'useful', vars: ['days', 'band'], title: 'Your exam is {days} days away', body: 'Your last band was {band}. One timed test today shows what to work on.' },
      { id: 'exam-en-10', tone: 'warm', vars: ['days'], title: "{days} days. Let's make them count", body: "A little practice each day adds up. Start with today's drills." },
    ],
    banglish: [
      { id: 'exam-bn-1', tone: 'useful', vars: ['days'], title: 'Exam er ar {days} din', body: 'Ekta full mock dile bujhbe tumi kothay acho, ar kaj korar shomoy o thakbe.' },
      { id: 'exam-bn-2', tone: 'useful', vars: ['days', 'weakest'], title: 'Exam er {days} din baki 📅', body: 'Tomar weakest skill {weakest}. Ei shoptahe ekta {weakest} test sobcheye kaje lagbe.' },
      { id: 'exam-bn-3', tone: 'warm', vars: ['days'], title: '{days} din baki, tumi parbe', body: 'Roj olpo olpo practice cramming er cheye bhalo. Ajker drill ready.' },
      { id: 'exam-bn-4', tone: 'useful', vars: ['days'], title: 'Exam countdown: {days} din', body: 'Aj ekta timed test dao, ashol exam ta chena chena lagbe.' },
      { id: 'exam-bn-5', tone: 'useful', vars: ['weakest', 'days'], title: '{weakest} e focus koro', body: 'Exam er {days} din baki. {weakest} ekhon tomar shobcheye kom, tai ekhanei labh beshi.' },
      { id: 'exam-bn-6', tone: 'useful', vars: ['days', 'band'], title: '{days} din baki, cholo shuru kori', body: 'Tomar shesh band {band}. Aj ekta test diye dekho kon jaygay kaj korte hobe.' },
    ],
  },
  'exam-tomorrow': {
    en: [
      { id: 'examtmr-en-1', tone: 'warm', vars: [], title: 'Your exam is tomorrow 🍀', body: 'No cramming tonight. A light review and a good sleep help most.' },
      { id: 'examtmr-en-2', tone: 'warm', vars: [], title: 'Exam day is tomorrow', body: "You've put in the work. Rest well and trust your practice." },
      { id: 'examtmr-en-3', tone: 'useful', vars: [], title: "Tomorrow's the day", body: "Skim your notes, sleep early, eat breakfast. You're ready for this." },
      { id: 'examtmr-en-4', tone: 'useful', vars: [], title: 'One day to go', body: 'A short warm-up drill is plenty today. Save your energy for tomorrow.' },
      { id: 'examtmr-en-5', tone: 'fun', vars: [], title: 'Big day tomorrow 💪', body: 'Breathe, watch the clock, and take it one section at a time.' },
      { id: 'examtmr-en-6', tone: 'warm', vars: [], title: 'Good luck tomorrow', body: "Read every question twice and keep an eye on the time. You've got this." },
    ],
    banglish: [
      { id: 'examtmr-bn-1', tone: 'warm', vars: [], title: 'Kal tomar exam 🍀', body: 'Aj rate cramming na. Halka revise ar bhalo ghum shobcheye kaje lagbe.' },
      { id: 'examtmr-bn-2', tone: 'warm', vars: [], title: 'Kal exam er din', body: 'Tumi onek practice korecho. Bhalo kore ghumao, nijer upor bhorsha rakho.' },
      { id: 'examtmr-bn-3', tone: 'useful', vars: [], title: 'Ar matro ek din', body: 'Aj sudhu ekta chhoto warm-up drill jothesto. Energy kal er jonno rakho.' },
      { id: 'examtmr-bn-4', tone: 'warm', vars: [], title: 'Kal er jonno shubhokamona', body: 'Proti ta question mon diye poro, shomoy er dike kheyal rakho. Tumi parbe.' },
    ],
  },
  cold: {
    en: [
      { id: 'cold-en-1', tone: 'fun', vars: ['skill', 'idle'], title: 'Your {skill} misses you', body: '{idle} days since your last {skill} test. One test keeps it warm.' },
      { id: 'cold-en-2', tone: 'useful', vars: ['skill', 'band', 'idle'], title: '{skill} was at {band} last time', body: "It's been {idle} days. One test now keeps that band in reach." },
      { id: 'cold-en-3', tone: 'fun', vars: ['skill', 'band', 'idle'], title: 'Time to wake up your {skill} ⏰', body: 'Last test: band {band}, {idle} days ago. A single part is enough today.' },
      { id: 'cold-en-4', tone: 'useful', vars: ['skill', 'band'], title: 'Keep your {skill} {band} warm', body: 'Skills fade after a week or two off. One short test keeps you sharp.' },
      { id: 'cold-en-5', tone: 'useful', vars: ['idle', 'skill'], title: '{idle} days without {skill}', body: 'Your other skills are moving. A quick {skill} test keeps things balanced.' },
      { id: 'cold-en-6', tone: 'fun', vars: ['skill', 'idle'], title: '{skill} has been on holiday', body: '{idle} days off is plenty. Bring it back with one test today.' },
      { id: 'cold-en-7', tone: 'useful', vars: ['skill', 'band'], title: 'A quick {skill} check-in?', body: 'Your last {skill} band was {band}. One test shows where it stands now.' },
      { id: 'cold-en-8', tone: 'fomo', vars: ['skill', 'idle', 'band'], title: "Don't let {skill} go cold", body: "It's been {idle} days. One test today keeps your {band} steady." },
      { id: 'cold-en-9', tone: 'useful', vars: ['skill'], title: 'Mix in some {skill} today', body: 'Practising every skill each week keeps your overall band even.' },
      { id: 'cold-en-10', tone: 'warm', vars: ['skill', 'idle', 'band'], title: '{skill}: last seen {idle} days ago', body: 'Pick up where you left off at band {band}. One part is enough for today.' },
    ],
    banglish: [
      { id: 'cold-bn-1', tone: 'fun', vars: ['skill', 'idle'], title: 'Tomar {skill} tomake miss korche', body: 'Shesh {skill} test {idle} din age. Ekta test diye abar shuru koro.' },
      { id: 'cold-bn-2', tone: 'useful', vars: ['skill', 'band', 'idle'], title: '{skill} e shesh band {band}', body: '{idle} din hoye geche. Ekhon ekta test dile band ta dhore rakhte parbe.' },
      { id: 'cold-bn-3', tone: 'fun', vars: ['skill', 'idle'], title: '{skill} ke abar jagao ⏰', body: '{idle} din bishram jothesto. Aj ekta part korlei hobe.' },
      { id: 'cold-bn-4', tone: 'useful', vars: ['skill'], title: '{skill} ektu thanda hoye jacche', body: 'Onno skill gulo egocche. Ekta {skill} test diye balance rakho.' },
      { id: 'cold-bn-5', tone: 'useful', vars: ['skill', 'band'], title: 'Ekta {skill} check-in?', body: 'Tomar shesh {skill} band {band}. Ekta test dile bujhbe ekhon kothay ache.' },
      { id: 'cold-bn-6', tone: 'warm', vars: ['skill'], title: 'Aj ektu {skill} hok', body: 'Proti shoptahe shob skill practice korle overall band shoman thake.' },
    ],
  },
  'milestone-near': {
    en: [
      { id: 'milestone-en-1', tone: 'fomo', vars: ['milestone'], title: 'A {milestone}-day streak is close 🏆', body: "Practise today and tomorrow, and you're there. A drill a day does it." },
      { id: 'milestone-en-2', tone: 'useful', vars: ['milestone'], title: 'Two days to day {milestone}', body: 'Keep today going and tomorrow is the big one.' },
      { id: 'milestone-en-3', tone: 'fun', vars: ['milestone'], title: 'Milestone alert: day {milestone}', body: "Just today and tomorrow to go. You're nearly there." },
      { id: 'milestone-en-4', tone: 'useful', vars: ['milestone'], title: 'Almost {milestone} days in a row', body: 'One practice today and one tomorrow. A 2-minute drill counts each time.' },
      { id: 'milestone-en-5', tone: 'warm', vars: ['milestone'], title: "Look how far you've come", body: "Day {milestone} is two practice days away. Today's drill gets you halfway." },
      { id: 'milestone-en-6', tone: 'fun', vars: ['milestone'], title: 'Day {milestone} is nearly here 🎉', body: 'Practise today to set it up for tomorrow. Short drills count.' },
      { id: 'milestone-en-7', tone: 'fomo', vars: ['milestone'], title: '{milestone} days, so close', body: "Today and tomorrow, that's all. Then it's a milestone." },
      { id: 'milestone-en-8', tone: 'fun', vars: ['milestone'], title: 'Your next badge: {milestone} days', body: 'Practise today and tomorrow to earn it.' },
      { id: 'milestone-en-9', tone: 'warm', vars: ['milestone'], title: 'Nearly a {milestone}-day streak', body: 'Keep it going today. Tomorrow you could be celebrating.' },
      { id: 'milestone-en-10', tone: 'useful', vars: ['milestone'], title: 'Day {milestone} is in sight', body: 'Two more practice days. Start with a quick drill today.' },
    ],
    banglish: [
      { id: 'milestone-bn-1', tone: 'fomo', vars: ['milestone'], title: '{milestone} diner streak prai hoye gelo 🏆', body: 'Aj ar kal practice korlei hoye jabe.' },
      { id: 'milestone-bn-2', tone: 'useful', vars: ['milestone'], title: 'Day {milestone} ar dui din dure', body: 'Aj ta cholte thakuk, kal-i boro din.' },
      { id: 'milestone-bn-3', tone: 'fun', vars: ['milestone'], title: 'Milestone alert: {milestone} din', body: 'Sudhu aj ar kal. Prai pouchhe gecho.' },
      { id: 'milestone-bn-4', tone: 'useful', vars: ['milestone'], title: 'Prai {milestone} din tana', body: 'Aj ekbar, kal ekbar practice. 2 minuter drill o count hoy.' },
      { id: 'milestone-bn-5', tone: 'warm', vars: ['milestone'], title: 'Dekho koto dur esecho', body: 'Day {milestone} ar dui practice din dure. Ajker drill e ordhek shesh.' },
      { id: 'milestone-bn-6', tone: 'fun', vars: ['milestone'], title: 'Tomar porer badge: {milestone} din', body: 'Aj ar kal practice korlei pabe 🎉' },
    ],
  },
  weekly: {
    en: [
      { id: 'weekly-en-1', tone: 'useful', vars: ['active', 'tests', 'drills', 'weakest'], title: 'Your week at a glance', body: "Practice days: {active}. Tests: {tests}. Drill cards: {drills}. Next week's focus: {weakest}." },
      { id: 'weekly-en-2', tone: 'fun', vars: ['active', 'tests', 'drills', 'weakest'], title: 'Weekly recap 📊', body: '{active} of 7 days, {tests} tests, {drills} drills. Nice work. Next up: {weakest}.' },
      { id: 'weekly-en-3', tone: 'warm', vars: ['active', 'tests', 'drills', 'weakest'], title: 'Week done ✅', body: '{active} of 7 days, {tests} tests and {drills} drill cards. Next week, more {weakest}?' },
      { id: 'weekly-en-4', tone: 'useful', vars: ['active', 'tests', 'drills', 'weakest'], title: "Here's your week", body: 'Practised {active} of 7 days. Tests: {tests}. Drills: {drills}. Next focus: {weakest}.' },
      { id: 'weekly-en-5', tone: 'fun', vars: ['active', 'tests', 'drills', 'weakest'], title: 'Sunday scoreboard', body: 'Days: {active}/7. Tests: {tests}. Drills: {drills}. Next target: {weakest}.' },
      { id: 'weekly-en-6', tone: 'useful', vars: ['active', 'tests', 'drills', 'weakest'], title: '{active} of 7 days this week', body: 'Plus {tests} tests and {drills} drill cards. {weakest} is the one to push next.' },
      { id: 'weekly-en-7', tone: 'warm', vars: ['active', 'tests'], title: 'Look at your week', body: '{active} of 7 days practised, {tests} tests done. Small steps like these add up.' },
      { id: 'weekly-en-8', tone: 'useful', vars: ['drills', 'tests', 'weakest'], title: 'Your weekly check-in', body: 'This week: {drills} drill cards, {tests} tests. Next week, a {weakest} test or two?' },
      { id: 'weekly-en-9', tone: 'useful', vars: ['active', 'tests', 'weakest'], title: 'Weekly recap: {active}/7 days', body: 'Tests done: {tests}. Your biggest gain next week is in {weakest}.' },
      { id: 'weekly-en-10', tone: 'warm', vars: ['active', 'weakest'], title: 'One week down', body: 'This week: {active} of 7 days. Next week, {weakest} is where practice pays off most.' },
    ],
    banglish: [
      { id: 'weekly-bn-1', tone: 'useful', vars: ['active', 'tests', 'drills', 'weakest'], title: 'Tomar shoptaher hishab', body: 'Practice din: {active}. Test: {tests}. Drill card: {drills}. Porer focus: {weakest}.' },
      { id: 'weekly-bn-2', tone: 'fun', vars: ['active', 'tests', 'drills', 'weakest'], title: 'Weekly recap 📊', body: '7 diner {active} din, {tests} ta test, {drills} ta drill. Darun! Porer target: {weakest}.' },
      { id: 'weekly-bn-3', tone: 'warm', vars: ['active', 'tests', 'drills', 'weakest'], title: 'Shoptaho shesh ✅', body: '{active}/7 din, {tests} ta test, {drills} ta drill. Porer shoptahe {weakest} e beshi shomoy dao.' },
      { id: 'weekly-bn-4', tone: 'fun', vars: ['active', 'tests', 'drills', 'weakest'], title: 'Sunday scoreboard', body: 'Din: {active}/7. Test: {tests}. Drill: {drills}. Porer target: {weakest}.' },
      { id: 'weekly-bn-5', tone: 'warm', vars: ['active', 'tests'], title: 'Tomar shoptaho ta dekho', body: '7 diner {active} din practice, {tests} ta test. Erokom chhoto step-i onek dur niye jay.' },
      { id: 'weekly-bn-6', tone: 'useful', vars: ['active', 'weakest'], title: 'Ek shoptaho shesh', body: 'Ei shoptahe {active}/7 din. Porer shoptahe {weakest} practice korle labh beshi.' },
    ],
  },
  'win-back': {
    en: [
      { id: 'winback-en-1', tone: 'fun', vars: ['band'], title: 'Your {band} is ready for its next step 👀', body: 'Pick up where you left off. One short test or a 2-minute drill.' },
      { id: 'winback-en-2', tone: 'warm', vars: ['idle'], title: 'Ready when you are', body: "It's been {idle} days. Your drills and progress are right where you left them." },
      { id: 'winback-en-3', tone: 'useful', vars: ['idle'], title: 'A 2-minute way back in', body: '{idle} days off is fine. A short drill is the easiest restart.' },
      { id: 'winback-en-4', tone: 'fun', vars: ['idle'], title: 'Your drills miss you', body: "They've waited {idle} days. Five quick cards and you're back." },
      { id: 'winback-en-5', tone: 'useful', vars: ['band'], title: 'Pick up from band {band}', body: 'Your progress is saved. One test today shows where you are now.' },
      { id: 'winback-en-6', tone: 'warm', vars: [], title: 'Quick practice today?', body: 'No pressure. A single drill or one test part gets you back on track.' },
      { id: 'winback-en-7', tone: 'useful', vars: ['band'], title: 'Still aiming higher than {band}?', body: "One test today and you're moving again. Your results are all saved." },
      { id: 'winback-en-8', tone: 'warm', vars: ['idle'], title: 'Welcome back anytime', body: '{idle} days away. Start small: 5 drill cards take about 2 minutes.' },
      { id: 'winback-en-9', tone: 'fomo', vars: ['idle', 'band'], title: 'Skills stay sharp with use', body: "It's been {idle} days. One short test keeps your {band} in reach." },
      { id: 'winback-en-10', tone: 'useful', vars: ['band'], title: 'Back to it?', body: 'Your last band was {band}. A quick test today shows how much has stuck.' },
    ],
    banglish: [
      { id: 'winback-bn-1', tone: 'fun', vars: ['band'], title: 'Tomar {band} ke ek dhap upore nao 👀', body: 'Jekhane chhere chile shekhan theke shuru koro. Ekta chhoto test ba drill.' },
      { id: 'winback-bn-2', tone: 'warm', vars: ['idle'], title: 'Tumi ready hole amrao ready', body: '{idle} din hoye geche. Tomar drill ar progress shob jaygamoto ache.' },
      { id: 'winback-bn-3', tone: 'useful', vars: ['idle'], title: 'Ferar shobcheye shohoj upay', body: '{idle} din break thik ache. Ekta 2 minuter drill diye abar shuru koro.' },
      { id: 'winback-bn-4', tone: 'fun', vars: ['idle'], title: 'Tomar drill gulo opekkha korche', body: '{idle} din dhore. 5 ta card korlei abar shuru hoye jabe.' },
      { id: 'winback-bn-5', tone: 'useful', vars: ['band'], title: 'Band {band} theke abar shuru', body: 'Tomar shob progress save ache. Aj ekta test dile bujhbe ekhon kothay acho.' },
      { id: 'winback-bn-6', tone: 'warm', vars: [], title: 'Ektu practice hobe naki?', body: 'Kono chap nei. Ekta drill ba ekta test part diyei abar shuru kora jay.' },
    ],
  },
  goodbye: {
    en: [
      { id: 'goodbye-en-1', tone: 'warm', vars: [], title: "We'll give you some space 💛", body: "No more daily reminders. Your drills and progress stay here whenever you're ready." },
      { id: 'goodbye-en-2', tone: 'useful', vars: [], title: 'Taking a break from reminders', body: "We'll only send your weekly recap and exam countdown. Everything you've done is saved." },
      { id: 'goodbye-en-3', tone: 'warm', vars: [], title: "We'll go quiet for now", body: 'Your streak history, drills and bands are saved. Come back anytime.' },
      { id: 'goodbye-en-4', tone: 'warm', vars: [], title: 'Fewer reminders from now on', body: 'Life gets busy. Your progress is safe and waiting when you want it.' },
      { id: 'goodbye-en-5', tone: 'useful', vars: [], title: 'No more nudges for now', body: 'Just the weekly recap and exam reminders. You can turn those off in Settings.' },
      { id: 'goodbye-en-6', tone: 'warm', vars: [], title: "We'll stop the daily pings", body: "When you're ready, everything is right where you left it. Good luck 💛" },
      { id: 'goodbye-en-7', tone: 'useful', vars: [], title: 'Pausing your reminders', body: "Your saved work isn't going anywhere. Open the app whenever it suits you." },
      { id: 'goodbye-en-8', tone: 'warm', vars: [], title: 'Here when you need us', body: "We're easing off reminders. Your drills, results and progress all stay saved." },
    ],
    banglish: [
      { id: 'goodbye-bn-1', tone: 'warm', vars: [], title: 'Ektu space dicchi 💛', body: 'Ar roj reminder pathabo na. Tomar drill ar progress ekhanei thakbe, jokhon ready hobe.' },
      { id: 'goodbye-bn-2', tone: 'useful', vars: [], title: 'Reminder kom pathabo', body: 'Sudhu weekly recap ar exam countdown pabe. Tomar shob kaj save ache.' },
      { id: 'goodbye-bn-3', tone: 'warm', vars: [], title: 'Ekhon chup thakchi', body: 'Tomar streak history, drill ar band shob save ache. Jokhon khushi fire esho.' },
      { id: 'goodbye-bn-4', tone: 'warm', vars: [], title: 'Busy thakle shomossha nei', body: 'Tomar progress safe ache, tomar jonno opekkha korche.' },
      { id: 'goodbye-bn-5', tone: 'useful', vars: [], title: 'Roj er reminder bondho', body: 'Ready hole shob kichu jekhane chhilo shekhanei pabe. Shubhokamona 💛' },
    ],
  },
  'mock-ready': {
    en: [
      { id: 'mock-en-1', tone: 'useful', vars: ['parts'], title: 'Your mock is waiting', body: 'Still to do: {parts}. Finish it to get your full band.' },
      { id: 'mock-en-2', tone: 'useful', vars: ['parts'], title: 'Finish your mock test', body: 'Left: {parts}. Your answers so far are saved.' },
      { id: 'mock-en-3', tone: 'warm', vars: ['parts'], title: 'Your mock is part-done', body: "Pick up with {parts}. Everything you've done is saved." },
      { id: 'mock-en-4', tone: 'fun', vars: ['parts'], title: 'Your mock wants an ending 🎬', body: "Still to go: {parts}. Then you'll see your overall band." },
      { id: 'mock-en-5', tone: 'useful', vars: ['parts'], title: 'Ready to finish your mock?', body: 'Next: {parts}. Your full band report comes when it is done.' },
      { id: 'mock-en-6', tone: 'useful', vars: ['parts'], title: 'Complete your mock for a full band', body: 'You still have {parts}. Your earlier sections are saved.' },
      { id: 'mock-en-7', tone: 'warm', vars: ['parts'], title: 'Your mock is saved', body: 'When you have time, finish {parts} to see your overall band.' },
      { id: 'mock-en-8', tone: 'fomo', vars: ['parts'], title: 'Unfinished mock', body: "Left to do: {parts}. Finish it soon, while it's fresh." },
    ],
    banglish: [
      { id: 'mock-bn-1', tone: 'useful', vars: ['parts'], title: 'Tomar mock opekkha korche', body: 'Baki ache: {parts}. Shesh korle full band pabe.' },
      { id: 'mock-bn-2', tone: 'useful', vars: ['parts'], title: 'Mock ta shesh koro', body: 'Baki: {parts}. Ager uttor gulo save ache.' },
      { id: 'mock-bn-3', tone: 'fun', vars: ['parts'], title: 'Mock er baki ta korbe? 🎬', body: 'Ebar {parts}. Shesh hole overall band dekhte parbe.' },
      { id: 'mock-bn-4', tone: 'warm', vars: ['parts'], title: 'Tomar mock save ache', body: 'Shomoy pele {parts} shesh koro, overall band dekhe nao.' },
      { id: 'mock-bn-5', tone: 'fomo', vars: ['parts'], title: 'Mock ta complete koro', body: 'Baki: {parts}. Taratari shesh korle shob mone thakbe.' },
    ],
  },
};
