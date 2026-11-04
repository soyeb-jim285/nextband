# Google Play: Data safety answers (NextBand Android, app.nextband)

Paste these into Play Console → App content → Data safety. Update them when the app changes; the push-notifications phase adds a device ID (see the end).

## Overview questions
| Question | Answer |
|---|---|
| Does your app collect or share any of the required user data types? | **Yes** |
| Is all of the user data collected by your app encrypted in transit? | **Yes** (HTTPS only; cleartext is blocked in the network security config) |
| Do you provide a way for users to request that their data is deleted? | **Yes**: in the app (Settings → Delete account / Delete my guest data) and at https://nextband.app/privacy (email support@nextband.app) |
| Account deletion URL (asked separately under "Data deletion") | `https://nextband.app/privacy` (the "Your rights and contact" section explains in-app deletion and the email address) |

## Data types

"Shared" means sent to a third party for that party's own use. Sending data to **service providers who process it for us** (AI scoring, speech-to-text, email, payments) is **not** "sharing" under Google's definition, so every type below is **Collected: Yes, Shared: No**.

| Category → type | Collected | Shared | Optional? | Purposes | Why |
|---|---|---|---|---|---|
| Personal info → **Name** | Yes | No | Optional (guests have none) | App functionality, Account management | Account profile |
| Personal info → **Email address** | Yes | No | Optional (guests have none) | App functionality, Account management, Developer communications | Sign-in codes, password reset, product emails (opt-out) |
| Personal info → **User IDs** | Yes | No | Required | App functionality, Account management | Account/guest id on our server |
| Audio → **Voice or sound recordings** | Yes | No | Optional (only in speaking tests) | App functionality | Speaking answers are recorded, transcribed and scored |
| Messages / App activity → **Other user-generated content** | Yes | No | Required to use tests | App functionality | Essays, written answers, notes |
| App activity → **App interactions** | Yes | No | Required | App functionality, Analytics | Test attempts, scores, drill history, streak, minutes practised (shown back to the user) |
| App info and performance → **Crash logs** | **No** unless a crash SDK is added | | | | The app has no crash reporter today |
| Device or other IDs | **No** (until push notifications ship) | | | | |
| Financial info → Purchase history | **No** in the Play build | | | | The Play build sells nothing. Web purchases are tied to the account server-side, not collected by the app |
| Location, Contacts, Photos, Calendar, Health, Web browsing, Installed apps | **No** | | | | |

Is the data processed ephemerally? **No** for all of the above (it is stored in the account).

## Security practices section
- Data encrypted in transit: **Yes**
- Users can request deletion: **Yes**
- Committed to the Play Families policy: **No** (the app is not for children)
- Independent security review: **No**

## When push notifications ship (round B)
Add: **Device or other IDs → Yes, collected, not shared, required for notifications, purpose App functionality** (the Firebase Cloud Messaging token). The privacy policy already describes it.

## Other App content forms (for reference)
- **Privacy policy:** https://nextband.app/privacy
- **Ads:** No ads.
- **App access:** "All or some functionality is restricted" → add a reviewer login (a dedicated demo account; create it before submitting and note any Pro access it needs).
- **Content rating:** Education; no violence, sexual content, gambling, user-to-user chat or location sharing.
- **Target audience:** 18 and over (or 16+); not designed for children.
- **News app:** No. **COVID-19:** No. **Government app:** No. **Financial features:** None.
- **Trader status:** Trader (done). Terms of Service: https://nextband.app/terms
