# NextBand Android v2 — design spec

The Android app is rebuilt from scratch (branch `android-v2`). This file is the contract every screen follows.
The web app (`apps/web`) is the reference for **features, server calls, rules and copy**; it is **not** the reference
for layout. Android screens are designed for a phone held in one hand: thumb reach, sheets, sticky bottom actions,
gestures, haptics, edge-to-edge, IME-aware layouts.

The old app is still readable with `git show main:apps/android/<path>` (behaviour reference only; don't copy its UI).

## 1. Information architecture

```
Bottom bar (5):  Today · Listening · Reading · Writing · Speaking
```

Why: the four tests are what a learner opens the app to do, so each gets one tap. "Today" is the place that answers
"what should I do now": the full mock, due review cards, the coach's next step, recent results, progress. Everything
that is *about you* (history, mistakes, settings, plan) sits one tap away behind the avatar, the standard mobile
pattern, so the bar never needs a sixth slot.

| Where | What lives there |
|---|---|
| **Today** (tab) | greeting + streak · **Full mock hero** (start / resume / last mock band) · predicted bands (4 skills + overall vs target) · "Up next" (coach next step or weakest skill) · Study tools row: **Drill** (due badge), **Patterns**, **Your plan** (coach), **History** · recent results · exam countdown when an exam date is set |
| **Listening / Reading** (tabs) | mock strip · continue-unfinished card · Academic/General switch (reading) · Cambridge books + original tests grouped · per-test best band · single part / passage practice · spelling drill (listening) |
| **Writing** (tab) | mock strip · full test (T1+T2, 60 min) · Task 1 (Academic chart / General letter) · Task 2 · prompt bank entry · drafts · recent results |
| **Speaking** (tab) | mock strip · **Live examiner** hero (when `me.live`) · full recorded test · Part 1 / 2 / 3 practice · pending uploads · prompt bank entry · recent results |
| **You** (avatar on every tab root, top-right) | profile + plan/tier + tests left · target band, exam date, test type · History · Patterns · Drill · Your plan · Prompt bank · Settings (appearance, writing options, emails) · Help & legal · Sign out / delete account. Guest: big "Create account to keep your results" card |

**The full mock is visible everywhere it matters:** hero on Today; a slim "Full mock · 2 h 45 m · L→R→W→S" strip at the top of
each skill tab (becomes "Resume mock · Reading next" when one is open). A started mock owns the screen: its hub shows the four
sections as a vertical stepper.

**Pushed screens** (no bottom bar, back arrow / predictive back): runners (LR runner, writing editor, speaking session, live
examiner), results, mock start/hub/result, review session, mistakes, coach, history, bank, settings, auth.

Test runners are **immersive**: no bottom bar, status bar kept, back asks to leave (dialog) and keeps progress.

## 2. Visual language

Calm, exact, confident. One teal accent for "act here". Each skill has an identity hue used **only** on its icon tile, tab
indicator tint and small accents — never for text meaning. Band colours (good / warn / bad) mean "vs your target" only.

### Colour tokens (`design/Color.kt`, read via `NB.colors`)

| Token | Light | Dark | Use |
|---|---|---|---|
| bg | #F6F8FA | #0A111C | screen background |
| surface | #FFFFFF | #111B2B | cards, sheets, bars |
| surface2 | #EEF2F6 | #18243A | insets, tracks, chips, inputs |
| surface3 | #E3E9F0 | #22304A | pressed / selected neutral |
| ink | #0F172A | #E6EDF5 | primary text |
| muted | #536175 | #93A1B5 | secondary text (≥4.5:1 on bg and surface) |
| faint | #7A8AA0 | #64748B | hints, disabled, control outline (≥3:1) |
| line | #E3E8EF | #1D293B | hairlines (decorative) |
| brand | #0F766E | #2DD4BF | primary action, selection, links |
| onBrand | #FFFFFF | #042F2C | content on brand |
| brandSoft | #DDF3EF | #0E2F33 | selected chip / hero tint |
| brandText | #0B5F58 | #5EEAD4 | brand-coloured text on soft fills |
| good / goodText / goodSoft | #15803D / #166534 / #DCFCE7 | #4ADE80 / #4ADE80 / #0B2E1A | at/above target |
| warn / warnText / warnSoft | #B45309 / #92400E / #FEF3C7 | #FBBF24 / #FBBF24 / #33240A | 0.5–1.0 below |
| bad / badText / badSoft | #BE123C / #BE123C / #FFE4EA | #FB7185 / #FB7185 / #3B0A18 | ≥1.5 below, errors |
| onBad | #FFFFFF | #3B0613 | content on a bad fill (badges, destructive buttons) |
| listening / listeningSoft | #0369A1 / #E0F2FE | #38BDF8 / #0B2536 | skill identity |
| reading / readingSoft | #4338CA / #E0E7FF | #A5B4FC / #1E1B4B | skill identity |
| writing / writingSoft | #A21CAF / #FAE8FF | #E879F9 / #3A0D3F | skill identity |
| speaking / speakingSoft | #C2410C / #FFEDD5 | #FB923C / #3A1A0B | skill identity |

Every text/background pair is checked by `ContrastTest` (AA: 4.5 text, 3.0 icons/controls). No dynamic colour. Dark mode
lifts surfaces by colour, not shadow.

### Type (`design/Type.kt`)

Hanken Grotesk for the interface. Newsreader **only** for content the learner reads or writes (passages, prompts, cue
cards, essays, transcripts, model answers). Numbers that change use tabular figures (`fontFeatureSettings = "tnum"`).

| Style | Size/line | Weight | Use |
|---|---|---|---|
| display | 48/52 | Bold, -1% tracking | band hero numbers |
| heroTitle | 30/36 | SemiBold, -1.5% | the one title on a BrandHero, welcome or sign-in header |
| headline | 28/34 | SemiBold, -0.5% | large top-bar titles |
| title1 | 22/28 | SemiBold | section heroes, sheet titles |
| title2 | 18/24 | SemiBold | card titles |
| title3 | 16/22 | SemiBold | list row titles |
| body | 16/24 | Regular | body |
| bodySmall | 14/20 | Regular | secondary body |
| label | 14/20 | Medium | buttons, chips, tabs |
| caption | 12/16 | Medium | meta, overlines (overline = caption, uppercase, +6% tracking) |
| reading | 18/29 | Newsreader Regular | passages, essays (user can scale 0.85–1.4×) |
| readingTitle | 24/30 | Newsreader SemiBold | passage / prompt titles |

### Shape, space, elevation, motion

- Radii: 8 (chips, small tiles), 12 (inputs, inner tiles), 20 (cards), 28 (sheets top corners), full (FABs, pills, avatars). Buttons: 18 (Large), 14 (Medium), 11 (Small).
- Space: 4-pt grid. Screen gutter 20 dp. Card padding 16–20. Between sections 28. Between list rows 0 (hairline dividers inside a card).
- Touch targets ≥ 48 dp (Small buttons draw at 36 dp but reserve 48 via `minimumInteractiveComponentSize`). Primary buttons 56 dp tall in sticky bottom bars, 48 elsewhere.
- Elevation: cards are flat surface + 1 dp `line` border in light, no border in dark. Floating things (bottom bar, sheets, mini player) use tonal surface + subtle shadow.
- Motion: 120 / 220 / 350 ms, emphasized-decelerate easing. Shared-axis X for push, fade-through for tab switches, container-transform feel for hero → screen (scale+fade). Respect "remove animations" (`LocalReducedMotion`).
- Haptics: `LongPress`/`TextHandleMove`-style confirm tick on answer select, flag, submit, timer warnings.

### Iconography

Material Symbols Rounded style vector drawables (24 dp, 2 dp stroke feel) in `res/drawable/ic_*.xml`. Skill icons: headphones (L),
menu_book (R), edit_note (W), mic (S). Mock: `award_star`. Never emoji.

## 3. Component library (`design/components/`)

All screens use these; no one-off styling.

- **NbScaffold** — edge-to-edge scaffold. Variants: `TabRoot` (large collapsing title + avatar action), `Pushed` (center/small title, back), `Immersive` (custom top bar, no bottom bar).
- **NbTopBar / NbLargeTopBar** — M3 top app bars with our tokens; large one collapses on scroll.
- **NbButton** — `Primary` (filled brand), `Secondary` (surface2 fill), `Outline`, `Text`, `Destructive`; sizes `Large` (56) / `Medium` (48) / `Small` (36); leading icon; loading state with spinner; full-width option.
- **StickyActionBar** — bottom-pinned bar with 1–2 buttons, sits above nav bar insets & IME, hairline on top.
- **NbCard** — surface card, optional onClick ripple, `tone = Default | Hero(brandSoft) | Skill(skill soft)`.
- **SkillTile** — rounded-12 square with skill soft background + skill-coloured icon. Sizes 40 / 56.
- **BandPill** — "6.5" in a small rounded rect, coloured by band vs target (good/warn/bad soft bg + text). `BandRing` — circular progress ring (band/9) with number centred, for heroes.
- **CriterionBar** — label, band value, track with fill and target tick.
- **NbListRow** — leading (icon/tile), title, subtitle, trailing (chevron / pill / switch), 64 dp min; group inside a card with dividers.
- **SectionHeader** — overline caption + optional "See all" text button.
- **Segmented** — 2–4 option segmented control (surface2 track, surface thumb sliding).
- **NbChip / FilterChipRow** — horizontally scrolling chips.
- **NbTabs** — scrollable/primary tab row with brand indicator.
- **ProgressDots / LinearMeter / Stepper** (vertical stepper for mock sections).
- **NbSheet** — ModalBottomSheet wrapper (drag handle, 28 radius, title, content, sticky actions). **ConfirmDialog**.
- **States**: `Skeleton` shimmer blocks (respect reduced motion), `EmptyState` (icon, title, body, action), `ErrorState` (message + Retry), `LoadingButton`.
- **TimerPill** — monospace-tabular countdown; turns warn at 5 min, bad at 1 min, with haptic tick.
- **AudioMiniPlayer** — play/pause, scrubber, speed; exam variant hides seek.
- **Avatar** — initials on brandSoft; guest shows person icon.
- **MockStrip** — the slim full-mock entry on skill tabs.
- **Snackbars** through one `NbSnackbarHost` in the root scaffold (`LocalSnack`).

## 4. Screen inventory (✓ = must exist in v2)

Auth & onboarding
- ✓ Welcome (logo, 3-line value prop, "Start free" = guest sign-in, "I have an account")
- ✓ Sign in / Sign up / Verify email code / Forgot / Reset (one flow screen with steps)
- ✓ Onboarding (3 steps after first sign-in: test type Academic/General, target band picker, exam date optional) → `PUT /api/settings` + local pref for test type

Tabs
- ✓ Today, Listening hub, Reading hub, Writing hub, Speaking hub (see IA)

Listening & Reading
- ✓ Test detail sheet (parts/passages, best band, Exam vs Practice mode, single part pick, continue/restart)
- ✓ Reading runner: passage full-screen + **draggable questions sheet** (peek shows current question group; drag up to answer; split view ≥ 600 dp). Paragraph letters, highlight + note on long-press, text size control.
- ✓ Listening runner: pinned audio mini-player (exam: no seek, all parts once; practice: scrub ±5 s, speed), questions list, review countdown.
- ✓ Question types: gap fill (inline in text/table/notes), MCQ, choose-N, T/F/NG & Y/N/NG, matching dropdowns, word box, map/diagram labelling, zoomable figure.
- ✓ Answer navigator sheet (grid 1–40, answered/flagged/current), flag, prev/next, autosave, timer, leave/submit dialogs.
- ✓ LR result: band hero, raw /40, by part & question type, answers list (filter wrong), tap → context, transcript with player, passage, retake.
- ✓ Spelling drill (listening spelling mistakes, TTS "hear the word").

Writing
- ✓ Task picker / prompt detail sheet (shuffle, from bank)
- ✓ Editor: collapsible prompt header (chart drawn natively for T1 academic), editor in Newsreader, word count + timer in a bar that rides above the keyboard, plan notes sheet, paste blocking, autosave drafts, 5/1-min warnings, auto-submit, full test switching T1/T2.
- ✓ Writing result: band hero + 4 criteria, tabs Overview / Essay (inline marks) / Structure / Language / Improve (model rewrite).

Speaking
- ✓ Mic check, session: Part 1/3 Q&A (examiner audio, record, next), Part 2 cue card with 1-min prep ring then 2-min talk, auto stop, pending upload queue with retry.
- ✓ Live examiner (LiveKit): connecting, examiner speaking/listening states with a voice orb, part indicator, captions toggle, end, reconnect.
- ✓ Speaking result: band hero + criteria, tabs Overview / Transcript (with audio) / Fluency (pace chart, pauses) / Language / Improve; analysing, failed (retry), no-speech states.

Mock
- ✓ Mock start (Academic/General, own or Cambridge, rules, tests left, resume/replace)
- ✓ Mock hub (vertical stepper L→R→W→S with transitions/break screens, speaking mode choice)
- ✓ Mock result (overall + 4 bands)

Study tools & you
- ✓ Drill (type the fix, check with a word diff, Got it / Not yet, skip for good, one pattern or ahead of schedule, spelling word from the test recording with TTS fallback)
- ✓ Patterns (what keeps coming back: tests, trend, Drill per pattern; the mistake log under it, add to drills) and "Mistakes you have made before" on results
- ✓ Coach (report: summary, priorities, plan; "updating" polling; feedback thumbs)
- ✓ History (bucketed by date, skill filter chips, swipe-to-delete with undo)
- ✓ Prompt bank (search, skill/part/topic filters)
- ✓ You / profile, Settings, Plan & limits (tier, tests left; `play` channel is consumption-only: plain non-clickable "upgrade at nextband.app" text, no prices/links/checkout; `web` channel APK (`-Pnextband.channel=web`) keeps the PayStation checkout in a Custom Tab; plan re-read on foreground and on Plan open), Help/legal (Custom Tabs to nextband.app pages)
- ✓ Limit / fair-use / busy sheets (`GateHost`)
- ✓ Streak (`feature/streak/`, `GET /api/streak`, docs/engagement/notifications-and-streaks.md §3): Today card under the bands (flame + count, ❄ freezes, 5-week heatmap, "N of the last 35 days"; after 18:00 with nothing done and a 2+ streak it warns and offers Drill), streak screen (week strip, freezes, milestone meter, longest, 6-month heatmap scrolled to today, repair banner, one CTA: drills due else a practice test), daily celebration sheet once per local day after a result or drill summary (N-1 → N; reduced motion only changes the numbers). Colour: `warn` only on the flame and count; heatmap is brandSoft → brand by fill strength (`lerp`), frozen = ice tint + ❄, today outlined

## 5. Architecture

```
com.soyeb.ieltspractice
  App.kt  MainActivity.kt                 Application + single activity (splash, edge-to-edge)
  AppGraph.kt                             manual DI: Api, Session, Prefs, Pending, Player…; LocalGraph
  data/                                   no Compose imports
    net/Api.kt                            OkHttp + kotlinx.serialization; one suspend fn per endpoint; ApiError
    net/Json.kt
    model/*.kt                            @Serializable models per domain (Me, Attempt, Prompt, Lr, Mock, Cards, Coach, Progress, Quota)
    Session.kt                            token store (Keystore DataStore), me StateFlow, sign in/out, guest
    Prefs.kt                              DataStore: appearance, test type, reading text scale, onboarding done
    Pending.kt                            pending speaking uploads
    demo/                                 DemoInterceptor serving fixtures.json (screenshots)
  media/                                  Recorder, Player (Media3), LiveKit examiner, Tts
  design/                                 tokens + theme + components (section 3)
  nav/                                    Routes (type-safe @Serializable), NbNavHost, BottomBar, Nav helper
  feature/<name>/                         one package per feature: screens + ViewModel-ish state holders + logic
    today, lr (listening+reading), writing, speaking, live, mock, result, review, mistakes, coach, history, bank, you, auth
```

- State: each screen has a small `XxxState` holder (`remember…` or `ViewModel` from lifecycle-viewmodel-compose) exposing
  `StateFlow<UiState>`; `UiState` is `Loading | Error | Ready(data)`. Use the shared `rememberLoad {}` helper.
- Navigation: Navigation Compose type-safe routes. Each feature exposes `fun NavGraphBuilder.<feature>Graph(nav: Nav)` in
  `feature/<name>/<Name>Graph.kt`, registered in `nav/NbNavHost.kt`. Routes for a feature live in that feature's graph file.
- Demo mode (screenshots, CI): launch extras pick a start route + theme; `DemoInterceptor` serves fixtures. Every screen has a
  demo entry in `nav/ScreenCatalog.kt` so `ScreenshotTest` records it in light and dark.
- Logic that can be unit-tested (band maths, timers, LR answer normalisation, word counts) lives in plain Kotlin files with tests.

## 6. Quality bar

- Every list screen has loading skeleton, empty state, error with retry, pull-to-refresh.
- Every destructive action: confirm dialog or undo snackbar.
- TalkBack: content descriptions on icon buttons, `semantics { heading() }` on section titles, merged rows.
- Font scale 200 % must not clip primary actions (use `heightIn(min=)`, not fixed heights, for text containers).
- Landscape / ≥ 600 dp: the Listening & Reading runner goes two-pane; tab and pushed lists cap at 720 dp, centred. Content stays clear of side cutouts and nav bars (root horizontal safe-drawing insets).
- No hard-coded colours or sizes in features: tokens only.
