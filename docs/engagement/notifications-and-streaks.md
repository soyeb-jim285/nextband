# Streaks and notifications: plan

Status: draft for the owner, 2026-11-05. It covers web, Android, server, and later iOS.

## 1. Goal and how we measure it

The goal is more students practising on more days. Subscriptions follow from that, because students who practise often reach the free limit and see real progress.

| Metric | Target after 8 weeks |
|---|---|
| D7 retention: the student practised again in days 2–7 | +30% relative to now |
| Days per week a student practises (weekly active users) | from ~1.5 to 2.5+ |
| Notification opened → practised within 2 h | 15%+ |
| Students who turn off all notifications | under 10% |
| Free → Pro, measured on web, for students with 7+ practice days | tracked; the email does the selling |

A 10% **holdout group** gets no engagement notifications at all, only results and account messages. This group shows whether the notifications actually help or only take credit for practice that would have happened anyway.

## 2. Rules we don't break

1. **Useful first.** Each notification says something true and specific about *this* student: their band, mistake, exam date or streak. No generic "We miss you!" spam.
2. **One nudge a day at most.** Notifications the student asked for (a result is ready) don't count toward this. At most 5 nudges a week.
3. **Quiet hours are 22:30–08:00** in the student's time zone. Asia/Dhaka is the default.
4. **Back off when ignored.** The fewer notifications a student opens, the fewer we send. We say goodbye honestly instead of nagging (§5.4).
5. **FOMO only when real.** A streak that will genuinely end tonight, or an exam that is genuinely 10 days away. No fake scarcity, no guilt, no "your friends are ahead of you".
6. **No selling in push notifications.** The Play build is consumption-only, so push notifications never mention Pro, prices or the website. Upgrade messages go **only by email**.
7. **Every type can be switched off** separately, and the switches are easy to find.

## 3. Streaks

### 3.1 What exists now
- `progress.ts` `streak()` counts consecutive **UTC** days that have an attempt.
- Web (overview) and Android (Today subtitle) show it as text only.
- Problems:
  - It uses UTC days, so a student in Dhaka who practises at 1 am gets it counted on the wrong day.
  - Drills don't count.
  - There is no protection when a student misses a single day.
  - Nothing celebrates the streak.

### 3.2 Rules
- **A practice day**, in the student's time zone, is any one of:
  - a submitted test part (any skill, practice or mock),
  - **5 drill cards** graded,
  - **10+ minutes** of listening or reading in one test.

  This is deliberately easy. A 2-minute drill session keeps the streak alive, the way one Duolingo lesson does.
- **Streak** is the number of consecutive practice days, ending today or yesterday.
- **Streak freezes:**
  - A student earns 1 freeze for every 7 days in a row, and can hold at most 2.
  - A missed day uses one freeze automatically, and the streak survives.
  - Freezes are free. They are never sold, because a paid freeze would be selling inside the app.
- **Repair:** if the streak breaks while the student has no freeze, they can repair it within 48 h by practising on 2 separate days.
  - The UI shows "Bring back your 12-day streak: practise today and tomorrow".
  - This turns losing a streak into a reason to come back instead of a reason to quit.
- **Milestones:** 3, 7, 14, 30, 50, 100, 200 and 365 days, each with a celebration.
- **Longest streak** is kept permanently.
- **Exam mode:** when an exam date is set and is 30 days away or less, the streak card also shows "18 days to exam · 9 practice days this month".

### 3.3 Data (server)
- `activity_days(user_id, day date, tests int, drills int, minutes int, primary key(user_id, day))`
  - Upserted by the hooks that already exist: attempt submit, drill grade, L/R autosave minutes.
  - `day` is computed in the student's time zone (from the `user_settings` timeZone, defaulting to Asia/Dhaka).
- `streaks(user_id pk, current, longest, last_day, freezes, frozen_days date[], repair_until timestamptz)`
  - Updated in the same transaction as the upsert, so the app shows the new streak straight away.
- A **nightly sweep** at 00:05 in each time zone applies freezes and breaks streaks for missed days.
- `GET /api/streak` returns current, longest, freezes, `today` (done / not yet), `week` (the last 7 days as states), `month` (a heatmap), `nextMilestone` and `repair`.
- The progress route reuses the same function, so web, Android and email all show one number.
- Guests get streaks too, and the streak moves to their account when they sign up (the guest-linking hook already exists).

### 3.4 UI

**Android and web: the streak chip.** It sits top-right on Today and on the web overview.
- It shows a flame with the number.
- The flame is grey until the student has practised today, then it fills with colour and does a short animation.
- Tapping it opens the streak screen.

**Streak screen:**

```
        🔥 12
     day streak
  ─────────────────────
   M  T  W  T  F  S  S
   ●  ●  ●  ❄  ●  ●  ○    ← today: hollow until done
  ─────────────────────
  ❄ 1 freeze saved · next one at day 14
  ▓▓▓▓▓▓▓▓▓▓░░  Next milestone: 14 days
  Longest: 21 days
  ─────────────────────
  [month heatmap, practice days tinted by how much]
  ─────────────────────
  "Keep it going: 4 drills are waiting"   [Drill now]
```

**Visual rules (same on web and Android):**
- It must look like NextBand, not Duolingo. The warm `warn` token goes **only** on the flame and the count. Everything else uses brand tokens.
- **Heatmap levels** use the brand colours, from `brandSoft` to `brand`:

  | Level | Meaning | Fill |
  |---|---|---|
  | 0 | none | surface2 |
  | 1 | a drill | light brand |
  | 2 | a test | medium brand |
  | 3 | a test + drills | full brand |

  Freeze days are a light ice tint with a ❄ marker. Today is outlined until done. The levels differ in fill strength and the markers, not only in hue, so they read for colour-blind users. Dark mode uses the dark tokens.
- **Home card** (web overview, Android Today, under the band cells):
  - 🔥 count + "day streak", with ❄ freezes on the right.
  - A **compact ~5-week GitHub-style heatmap**.
  - "You practised 23 of the last 35 days · Longest 21".
  - Tapping it opens the streak page.
  - When the streak is at risk, the line becomes "Ends at midnight: a 2-minute drill saves it" with a Drill button.
- **Streak page:** a **6-month heatmap** with month labels, plus the week strip, freezes, milestone progress and the longest streak.
- **Android, after the 2026-11 redesign** (apps/android/DESIGN.md has the detail): the Today card sits first on Today with **12 weeks as one row of squares** (one per week, darker for more practice days, so the mock hero and its CTA stay in view) and the line "N practice days in the last 12 weeks · Longest L", so the count matches the map. The streak page shows **13 weeks** that fill the width without scrolling. Web still follows the bullets above.

**Daily celebration.** It appears the first time each day the student finishes something that counts, as a bottom sheet over the result or drill summary:
- The flame grows from N−1 to N with the count, and the week strip fills in today.
- Copy is short and varies: "Day 12. You're on fire 🔥", "Back-to-back days! 2 🔥".
- At milestones it is bigger: confetti, a badge, and a **share card**, an image like "12-day streak on NextBand · Band 6.5 → 7.0". Shares on Facebook and Instagram bring free growth.
- With reduced motion turned on, nothing animates and only the numbers change.
- Accessibility: the flame has a content description such as "12 day streak, practised today".

**Streak at risk.** After 18:00, if the student hasn't practised today, the chip on Today pulses gently. The Today hero line becomes "Your 12-day streak ends at midnight. A 2-minute drill saves it".

**Broken streak.** The chip shows the repair offer for 48 h. After that the streak is gone, with a calm message: "New streak, fresh start. Your longest is 21."

## 4. Notification catalogue

Priority: P0 = the student asked for it; P1 = timely and valuable; P2 = nudge. The engine (§5) sends **at most 1 P1/P2 notification a day**, picking the highest-value one.

| # | Type | Trigger | Pri | Channel | Example (English / Banglish variants exist) |
|---|---|---|---|---|---|
| 1 | Result ready | Marking finished and the student hasn't seen it within 2 min | P0 | push, else email | "Your Task 2 essay got **6.5** 🎯 See the one thing that held you back" |
| 2 | Marking failed | Analysis failed | P0 | push | "We couldn't mark your Speaking Part 2. Tap to try again, it's free" |
| 3 | Streak saver | Streak ≥ 2, not practised today, at the student's usual time (or 20:30 at the latest) | P1 | push + **local fallback** | "🔥 12 days. One 2-minute drill keeps it alive" |
| 4 | Last call | Streak ≥ 7, still not practised, 21:30 | P1 (only if #3 was opened, or #3 wasn't sent) | local | "1.5 hours left on your 12-day streak ⏳" |
| 5 | Drills due | ≥ 5 cards due and the student didn't drill yesterday | P2 | push | "5 of your mistakes are ready to drill. They stick after 3 rounds" |
| 6 | Pattern alert | The same mistake category in 3 of the last 4 tests of one skill | P1 | push | "Articles tripped you up in 3 of your last 4 essays. 5 drills to fix it?" |
| 7 | Exam countdown | Exam date at 30, 21, 14, 7, 3 and 1 days | P1 | push + email | "14 days to your IELTS. A full mock this weekend shows where you stand" |
| 8 | Skill gone cold | A skill not practised for 7+ days while others were | P2 | push | "Your Listening was rising (6.0 → 6.5). One test keeps it warm 🎧" |
| 9 | Milestone near | The student reaches day N−1 of a milestone at their usual time | P2 (merged into #3 that day) | push | "Tomorrow is day 30. Big one 🏆" |
| 10 | Personal best | A new best band in a skill | P0 (in-app celebration, push only if the app is closed) | push | "New best: **7.0** in Reading 🚀" |
| 11 | Weekly recap | Sunday, at the student's usual time | P2 | push + email | "This week: 4 days, 3 tests, Reading +0.5. Next week's focus: Task 1 overviews" |
| 12 | Win-back | No activity for 3 / 7 / 14 days (see back-off) | P2 | push → email | Day 3: "Your 6.5 is still waiting to become 7 👀" |
| 13 | Goodbye | Back-off level 3 (§5.4) | P2 | push | "We'll stop reminding you. Your streak, drills and progress stay here when you're ready 💛" |
| 14 | Mock ready | A mock was started and not finished within 24 h | P2 | push | "Your mock is halfway done: Reading and Writing are left" |

**Email only (selling is allowed here):**
- welcome (done in round 2)
- limit reached (done in round 2)
- milestone 14/30 days + "Pro students practise unlimited"
- exam in 14 days + "unlock unlimited mocks"
- monthly progress report

Email uses the existing unsubscribe and campaign system.

## 5. The engine (server)

### 5.1 Pieces
- `notification_devices(user_id, token, platform, app_version, locale, created_at, last_seen)`, filled by `POST /api/notifications/devices`.
- `notification_prefs(user_id, results, practice, streak, exam, weekly, email_tips, quiet_start, quiet_end, language)`.
  - `results`, `practice`, `streak`, `exam` and `weekly` are on/off switches for the matching notification types.
  - Defaults: everything on, quiet hours 22:30–08:00, language from the device.
- `notification_log(id, user_id, type, template_id, channel, dedupe_key unique, scheduled_for, sent_at, opened_at, acted_at, holdout bool)`.
- **Event triggers:** attempt analysed/failed, card due, exam date set, and new personal best. These enqueue P0 notifications immediately.
- **Sweep every 15 min:** for users whose *send window* (§5.2) falls in the next 15 min, build the candidate list from §4, apply caps, back-off and quiet hours, and pick one.
- **Sender:**
  - FCM HTTP v1 for Android, with web push and iOS later through the same FCM project.
  - Email through the existing `sendEmail`.
  - A dead token (FCM `UNREGISTERED`) deletes its device row.

### 5.2 Timing per student
- The **usual time** is the median local hour of the student's activity starts over the last 21 days, minus 30 minutes. The window is ±15 min.
- With fewer than 4 data points, the default is **19:30** local. That's the evening study slot for students in Bangladesh, and it can be checked against our own data.
- **Never** during quiet hours. Fridays avoid 12:30–14:00 (Jumu'ah).

### 5.3 Choosing which message
1. Collect the candidates that apply today, for example streak saver, drills due and pattern alert.
2. Score each one as `priority weight × freshness × (1 − fatigue for this type)`.
3. Fatigue is the share of this type that the student ignored in the last 14 days.
4. Send the top one. Some types merge into one message, e.g. a milestone with the streak saver: "Tomorrow is day 30 🏆, and 5 drills are waiting".

### 5.4 Back-off
The back-off level is the number of P1/P2 notifications in a row that weren't opened.

| Level | Unopened in a row | Nudge rate |
|---|---|---|
| 0 | 0–2 | normal, up to 1 a day |
| 1 | 3–4 | every other day |
| 2 | 5–7 | 2 a week, only the highest-value types (pattern, exam, weekly) |
| 3 | 8 | send the **Goodbye** (#13), then only weekly recap and exam countdown |

Opening the app or practising resets the level to 0. P0 notifications (results) are never throttled.

### 5.5 Message library and learning which message works
- **Templates are written by our coding agents** (project rule): about 15 per type, roughly 200 in total, in English and Banglish.
- Each template has variables such as `{name} {streak} {band} {skill} {pattern} {days} {due}`.
- Agents also write a tone guide: warm, specific, a little playful; one emoji at most; under 90 characters; never shaming.
- Stored in `packages/core/notifications/templates.ts`, so every template is reviewed in a PR and covered by tests.
- **Bandit:** for each (type, template) the server keeps Beta(α, β), where success = practised within 2 h.
  - Thompson sampling picks the template.
  - New templates start with a prior equal to the type's average, so they get tried.
  - This finds the best-performing messages on its own, at no cost.
### 5.6 AI-written messages, targeted to each student

Templates fill in numbers. AI can also refer to what the student actually did, for example "You wrote 'informations' in both essays this week. 3 drills fix it for good". That is runtime app use, so OpenRouter is allowed under the project rules.

**Where AI writes and where templates do:**

| AI writes (needs the student's details) | Templates stay (simple and time-critical) |
|---|---|
| #6 Pattern alert: quotes their real mistake | #1/#2 Result ready / marking failed |
| #8 Skill gone cold: their real trend and their last weak point | #3/#4 Streak saver / last call |
| #11 Weekly recap: their week in 2 sentences, plus next week's focus | #5 Drills due, #7 Exam countdown |
| #12 Win-back: what they were improving at, and the easiest way back in | #9/#10/#13/#14 |

**How it works:**
1. **Facts packet.** The server builds a small JSON file per student with:
   - skill bands and trend
   - the top 3 mistake patterns, with 1–2 real examples each (quoted from their analysis, at most 12 words each)
   - streak, days since they last practised, exam date
   - what they practise most, their usual time, their language preference

   No email or phone number, and their essays are never sent in full.
2. **Batch, not live.** A nightly job writes tomorrow's AI candidates for students who are eligible for an AI type, so nothing waits on the model when it's time to send.
3. **Model.** `openai/gpt-6-luna` or `deepseek/deepseek-v4.1-flash` through OpenRouter, with a JSON output schema `{title, body, deepLink, factsUsed[]}`. Read the provider docs before building this.
4. **Guardrail validator.** Every AI message is checked, and any failure falls back to a template:
   - title 40 characters or less, body 110 or less, at most 1 emoji
   - **forbidden in push:** Pro, premium, upgrade, price, ৳, $, buy, subscribe, website, nextband.app, link
   - forbidden tone: guilt or shame words ("disappointed", "lazy", "failing", "you should be ashamed")
   - **every number and quoted mistake in the message must appear in the facts packet** (no invented progress)
   - language matches the student's preference
5. **Learning which works.** The AI message is one more option in that type's bandit, next to the templates. The data decides whether AI messages really get more practice than the best template. If AI loses on some type, the bandit stops picking it there without anyone stepping in.
6. **Email version.** The same facts packet can produce an AI email, which **may** mention Pro, e.g. "Pro would let you drill all 23 of these". Selling stays in email only.
7. **Logging.** The generated text, the validator result and the facts hash go to `notification_log`, so a bad message can be found, and the admin page shows AI against templates.

**Cost estimate:**
- About 700 tokens in and 80 out per message, roughly **$0.0002 per message**.
- AI is used on about 3 nudges per student per week.
- 1,000 weekly-active students → about **$2.60 a month**. 10,000 → about $26 a month.
- Per §2, a student never gets more than 1 nudge a day, AI or not.

Owner approval is needed before turning AI on. The template versions ship first, and AI can be switched on per type.

### 5.7 Measuring
- Opens: the app reports `opened_at` when launched from a notification tap.
- `acted_at` is set by the server when the student practises within 2 h of the send.
- An admin page (in the existing `admin/`) shows, per type and per template, the number sent, the open rate, the practice rate, the opt-out rate, and the gain against the holdout group.

## 6. Android client

- **Firebase Messaging dependency.** It needs `google-services.json`, provided by the owner. Without that file the build still compiles and push stays off, so CI and the website APK keep working.
- **Five notification channels**, each with its own Android-level switch:

  | Channel | Importance |
  |---|---|
  | Results | default |
  | Streak | high |
  | Practice reminders | default |
  | Exam | default |
  | Weekly recap | low |

- **Asking for permission (Android 13+):**
  - Never ask on first launch.
  - Ask the first time a result arrives in the app, or when the student first sets an exam date. The explainer sheet says "Want a ping when your band is ready and before your streak runs out?" with the buttons [Turn on] [Not now].
  - After "Not now", wait 7 days before asking again. After 2 refusals, stop asking and only show a Settings row.
- **Deep links:** each notification opens its exact screen (result, drill, streak, mock) through the existing Nav.
- **Notification buttons:** the streak saver has "Drill now" and "Later tonight". "Later tonight" sets a local reminder for 21:30.
- **Local fallback (WorkManager):**
  - Each day the app schedules the streak saver locally, using the usual time from `/api/streak`.
  - If the server's push arrives, or the student practises, the local reminder is cancelled.
  - This covers being offline and phones that kill FCM, such as Xiaomi and Oppo battery savers, which are common in Bangladesh.
- **Settings → Notifications:**
  - a switch for each type
  - quiet hours
  - a "Best time to remind me" picker (Auto by default)
  - language (English / Banglish)
  - a "Send me a test notification" button
- **The Updates bell stays as the in-app inbox,** and items a push opened are marked read.
- **Demo mode:** fixtures for the streak and the notification settings screens, so CI screenshots cover them.

## 7. Web
- The same streak chip, screen and celebration as on Android.
- Settings → Notifications with the same switches. Web push through FCM can come later; email covers web students now.
- The existing email campaigns gain the email-only list in §4.

## 8. Policy and privacy
- **Play payments:** no push mentions buying, Pro or the website. Selling happens only in email.
- **Play notifications:** permission is asked only when needed. Promotional nudges are switched off in Settings by default only in regions that require it (the EEA), and everything else defaults on with easy opt-out.
- **Data safety form:** declare the "Device or other IDs" (the FCM token) as collected for app functionality and not shared. Add the notification preferences to the privacy policy.
- **Deleting the account** deletes its device tokens and notification log.

## 9. Build phases

| Phase | Scope | Size |
|---|---|---|
| **A. Streaks** | Server activity days and streak in the student's time zone, freezes, repair, milestones, `/api/streak`; web and Android chip, streak screen, celebration and share card; tests; demo fixtures | 1 round |
| **B. Push setup** | Firebase project (owner), devices endpoint, FCM sender, Android Firebase Messaging, channels, permission flow, deep links, settings screen, P0 types (#1, #2, #10) | 1 round |
| **C. Engine** | Sweep, usual-time model, caps, quiet hours, back-off, candidates #3–#9, #12–#14, local fallback, template library + bandit, holdout, logging | 1–2 rounds |
| **D. AI and measuring** | AI-written messages (§5.6) with the validator, weekly recap, email-only sends, admin metrics page (AI against templates) | 1 round |
| **Review** | The same 12-area Claude + Sol panel, plus a 13th area, "Engagement & notifications quality", scored against §2 | after C |

## 10. What the owner needs to do
1. **Create a Firebase project** (free) at console.firebase.google.com and add the Android app `com.soyeb.ieltspractice`.
   - Download `google-services.json` and keep it out of git; CI gets it from a secret.
   - Create a **service account key** (Project settings → Service accounts → Generate new private key). It goes in Dokploy as `FCM_SERVICE_ACCOUNT_JSON`.
2. Approve AI-written messages (§5.6): about $2.60 a month per 1,000 weekly-active students.
3. After Phase B, update the Play **Data safety** form (I'll list the exact answers).

## 11. Decisions I made (change any of them)
- Streak freezes are free and earned, never sold.
- 5 drill cards count as a practice day.
- Notifications support English and Banglish, and the student chooses.
- 1 nudge a day, 5 a week, and quiet hours from 22:30 to 08:00.
- 10% holdout group.
- Email does all the selling, and push notifications never sell.
