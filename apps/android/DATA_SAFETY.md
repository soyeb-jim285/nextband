# Data safety (Play build)

Source for the Play Console Data safety form. Keep in step with the site privacy policy (apps/web routes/_app/_legal/privacy.tsx)
and with the code: update this file in the same change that adds a permission, a stored field or a processor.

All traffic is HTTPS (encrypted in transit). The Play build sells nothing in-app, so it collects no payment or phone data
(checkout exists only in the separate web-channel APK). No ads, no analytics SDK, no crash reporter, no session replay. The only Google SDK is Firebase Cloud Messaging (push), with
Analytics collection switched off in the manifest.

| Data type (Play category) | What exactly | Purpose | Shared with (processor) | Optional | Deleted by |
|---|---|---|---|---|---|
| Email address (Personal info) | Account email | Account, sign-in, service emails | Resend (email delivery) | Yes: guests have none | Account deletion |
| Name (Personal info) | Display name | Shown in the app and emails | Resend | Yes | Account deletion |
| Audio recordings (Audio) | Speaking answers, live examiner voice | Transcription and scoring | Soniox (speech to text), LiveKit (live session transport), stored in Cloudflare R2 | Yes: only Speaking uses the mic | Account deletion removes R2 audio |
| Other user-generated content | Writing essays, transcripts | AI feedback and band scores | OpenRouter (routes to Anthropic / OpenAI models) | Yes | Account deletion |
| App interactions / Other app activity | Test attempts, answers, scores, streaks | Progress, quotas | Not shared | No (needed for the service) | Account deletion |
| Device or other IDs | The Firebase Installation ID this phone gets push through (FCM), with app version and phone language; the Better Auth session token stays on the device | Push notifications (app functionality) | Google Firebase Cloud Messaging delivers the pushes (processor) | No: sent on every launch and sign-in; turning notifications off only stops them showing | Sign out removes it from the account (DELETE /api/push/token); account deletion deletes it |

Processors act on our behalf (Play: "service providers"), so the form may list them as not shared; declare them as collected.

## On the device only
- RECORD_AUDIO: used only while answering a Speaking question.
- POST_NOTIFICATIONS (Android 13+): asked after the first result or an exam date, never at launch (`feature/push/PushPrimer.kt`).
  Notification preferences (types, quiet hours, reminder time, language) are stored with the account on the server.
- Unsent recordings (filesDir, until the upload is confirmed), the session token, writing drafts, Listening/Reading notes and
  settings (DataStore). Backup and device transfer are off (`allowBackup=false`, `res/xml/data_extraction_rules.xml`).

## Deletion
- In the app: Settings > Account > Delete account (password), or "Delete my guest data" for guests
  (`feature/you/SettingsScreen.kt`, POST /api/auth/delete-user).
- Account deletion URL for Play Console: the site's account settings page (signed in). Uninstalling clears device data.
- Known gap: guest deletion fails once the guest session is over 24 h old (Better Auth freshAge); server fix pending.
