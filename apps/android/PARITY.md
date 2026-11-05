# Android parity with web and iOS

Status: done / partial / missing. Verified by unit tests (models against the shared fixtures, logic, wire formats) and CI screenshots in light and dark. Anything that needs a real microphone, a live server or a device is **not exercised by CI** (noted).

| Feature | Android | Notes |
| --- | --- | --- |
| Sign in, sign up, email verification code, forgot and reset password | done | `feature/auth/AuthScreen.kt`; guests can browse, sign-in is required for tests and personal data |
| Plan and buying Pro | done | Build channel `nextband.channel` (play default, web). play: consumption-only per Play Payments policy; Plan and the quota sheet show `GateText.STORE_UPGRADE` as plain text, no checkout. web (APK from the site, never bundleRelease): PayStation checkout as on the web. `/api/me` re-read on foreground so a site purchase unlocks without restart |
| Bearer token storage | done | Keystore-encrypted DataStore; 401 on a non-auth call signs out |
| Guest home | done | |
| Home dashboard (next up, predicted bands, band trend, streak card, criteria) | done | |
| Speaking hub (full test, live examiner, parts, pending uploads) | done | pending uploads survive failures and relaunch |
| Speaking session (mic check, Part 1/3 questions, Part 2 prep + long turn, auto stop 2:00, 15 min cap) | done | needs a device to verify recording; states are screenshotted via demo mode |
| Background upload, retry, resume across steps | done | `PendingStore` |
| Live examiner (LiveKit, the only engine) | done | `media/LiveKitExaminer.kt`, `media/LiveExam.kt`, `feature/speaking/LiveExamScreen.kt`: room from /api/live/livekit/start, data protocol on `ielts.events` / `ielts.control`, part recordings from LiveKit's mic sink; a dropped room offers score or reconnect; a mock waits until the session is linked (retry without rerunning). Not run against a live server in CI |
| Listening & Reading hubs (Cambridge book groups, Original practice tests, Academic / General chip, in-progress bar, best band), Exam / Practice sheet | done | open to everyone (the server hides Cambridge books unless the account is allow-listed); reached from the Home "Practise" card and Settings "More" (the tab bar stays five tabs); `feature/lr/LrHub.kt` |
| Listening & Reading runner: reading Passage / Questions tabs (side by side from 600 dp), listening sticky audio bar, every question type (gap inline in text and tables, mcq, choose-N, true/false/not given, match and word-box dropdowns, zoomable figures), navigator sheet with answered and flagged, flag, previous / next, autosave (~1 s, on stop, clock every 15 s), resume, leave and submit dialogs | done | Exam: reading 60-min countdown that auto-submits; listening plays all parts once with no pause or seek, then a 2-min review countdown and auto-submit (Media3 playlist). Practice: scrub, +-5 s, 0.75 / 1 / 1.25x, replay a part. Audio playback and a live server are not exercised by CI (demo mode shows fixed states) |
| Listening & Reading result (band, raw / 40, by part and type, answers with Wrong only, tap a number to see it in context, transcript with player, passage, Retake) and History rows | done | |
| Passage highlighting and notes (web: select text, kept per attempt) | done | kept on this device while the attempt is open (prefs `marks:<attemptId>`, `feature/lr/LrMarks.kt`), cleared on submit; not synced across devices |
| Full mock test (docs/mock-exam.md): start screen (Academic / General, own or Cambridge complete test, rules, tests left, resume or replace an open mock), hub with the Listening, Reading and Writing transitions, Speaking choice (recorded, live examiner when the server has it, or later), result with the server's overall band, "Full mock test" cards on Home and the four hubs, mock rows in History | done | `data/model/Mock.kt`, `feature/mock/` (`MockStart.kt`, `MockHub.kt`, `MockResult.kt`); the existing runners take an optional `mockId` (Listening / Reading submit and Exit, Writing session id and 15 s clock save, Speaking attempts, live attach) and return to the hub. Bands are never computed on the device. Not exercised by CI beyond unit tests (`MockTest`); no demo fixtures |
| Writing hub (full test, Task 1 academic/general, Task 2) | done | |
| Writing editor (timer from wall clock, 5/1 min warnings, word bar, plan, paste block, auto submit, drafts) | done | |
| Task 1 charts (line, bar, pie, table, process, map) | done | drawn natively |
| Result: speaking Overview, Transcript, Fluency (pace chart, markers, pauses), Language, Improve | done | audio player with seek and speed |
| Result: writing Overview, Essay, Structure, Language, Improve | done | |
| Result states: analysing, failed (retry), no speech, session (multi-part) | done | |
| Drill (type the fix, Got it / Not yet, skip, per pattern, ahead of schedule, spelling clip) | done | Cards are chosen by the server after each test |
| Patterns (trend, drill per pattern), mistake log, add to drills, "made before" on results | done | |
| History (bucketed by date, filters, full mock rows) | done | mock rows open the hub while open, the mock result after |
| Prompt bank (search, part, topic filters) | done | |
| Reading question-type drills (web: tab beside the full tests, `/api/lr/drills`) | missing | added on web main after this branch point |
| Improve hub (web: one hub with AI-chosen drills) | missing | added on web main after this branch point |
| Community mode: guest sessions (bearer, started at the first test), 1 test per week as a guest and per day signed in, tests left under Start, Home and Settings summary | done | `data/logic/Community.kt`, `nav/GateHost.kt`; copy from docs/community.md; unit-tested (`CommunityTest`) |
| Fair-use dialog (once per user per UTC day), limit panels (quota, balance, busy, too fast, live examiner unavailable), draft kept when a submit is refused | done | bottom sheet (`GateHost`) |
| Community balance meter (Home, Settings, panels) | done | neutral bar, amber under 10 % |
| Settings, Your API keys (OpenRouter; masked, validated by the server, replace, remove) | done | the key is never stored on the device |
| Guest result page ("Create an account to keep this result"), history/mistakes/review gates | done | |
| Settings (target band, writing options, sign out, delete account) | done | |
| Theme Ocean Teal, Newsreader + Hanken Grotesk, light and dark | done | AA contrast enforced by `ContrastTest` |
| Accessibility (content descriptions, 48dp targets, reduced motion) | partial | no TalkBack pass on a device |
| Offline use | partial | pending recordings queue; screens need the network |
| In-app updates (bell on every tab) | done | results, failed markings, cards due, open mock, exam countdown; refreshes on resume and while marking; read state per account (`UpdatesLogicTest`) |
| Streaks (local-day streak, freezes, repair, heatmap, daily celebration) | done | `feature/streak/`; Today card (compact 12 weeks, first on Today), streak screen (13 weeks, one grid tap target, one TalkBack stop per week, no cards), celebration sheet (once per local day, prefs `streak:celebrated`); rules unit-tested (`StreakLogicTest`); milestone confetti and share card not built yet |
| Push notifications | done (round B) | `feature/push/`: Firebase Cloud Messaging only, by Installation ID (`PUT /api/push/token` on every launch and sign-in, `DELETE` on sign-out); data-only messages drawn on five channels (Results, Streak, Practice reminders, Exam, Weekly recap); a tap opens the result / speaking session / mock / Notifications screen and reports `POST /api/push/opened`, marking its Updates rows read; Android 13+ explainer after a result opens or an exam date is set (7 days after "Not now", never after two); Settings > Notifications: a switch per type, quiet hours, reminder time (Auto), language, test push, Android's channel settings. Without google-services.json at build time push is off and everything else works. Logic unit-tested (`PushLogicTest`); delivery needs a device, not exercised by CI. Local streak fallback (WorkManager) and notification buttons are round C |
| Tablet / landscape layouts | partial | phone layouts scale; Listening & Reading reading and results go two-pane from 600 dp; tab and pushed lists cap at 720 dp centred; no navigation rail yet |
