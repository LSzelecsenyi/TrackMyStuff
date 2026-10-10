# Google Play Data safety preparation

Date: 10 October 2026.

This is a form-preparation inventory for the developer. It is not a completed Play Console submission, and it is not a legal certification of the privacy policy.

Labels used below:

- **Confirmed by code** means the current Android or backend source does this.
- **Requires backend/infrastructure confirmation** means the repository does not define the host, proxy, or database-provider behavior.
- **Requires developer/legal confirmation** means a person has to decide a fact the code does not contain.

Play Console itself was not opened. The deletion and privacy requirements cited here were read from Play Console Help on 10 October 2026:

- [User Data](https://support.google.com/googleplay/android-developer/answer/10144311)
- [Account deletion](https://support.google.com/googleplay/android-developer/answer/13327111)
- [Data safety](https://support.google.com/googleplay/android-developer/answer/10787469)

Those pages say an app that creates an account needs a public privacy-policy URL, an in-app deletion path, and a web deletion URL, and that freezing an account is not deletion. They also say retained data must be explained. The form questions inside Play Console were not filled in from this session.

## Do not answer “no data collected”

Google sign-in is required. The Strict service stores the Google subject, a verified email, a Strict user id, and a hash of the session token. That is collection by Strict.

Local-only training data is not automatically an answer of “collected” or “shared.” Play’s Data safety form is about data transmitted off the device. The table separates the two.

## Collected by the Strict service

Confirmed by code, unless a row says otherwise.

| Play-style data | Required or optional | Purpose in this version | Shared onward by Strict |
| --- | --- | --- | --- |
| Google subject and Strict user id | Required. There is no guest mode | Account | Not sent to another app by Strict. Google already issued the subject |
| Email, when Google marks it verified | Comes with required sign-in | Account | Not sent to another app by Strict |
| Session token hash | Required after sign-in | Authentication. Default lifetime in `application.yml` is 30 days | No |
| Founder application, qualifying-workout metadata, and tester-report text | Optional. Only if the person joins and submits | Founder Program | No |
| Pro Discovery trial times | Optional | Promotional access | No |
| Welcome Back times and qualifying workout id | Optional | Promotional access | No |
| Play purchase token, token hash, package, product id, state, expiry, order id | Optional, and paid checkout is not offered until products are configured | Subscription verification | The purchase itself is processed by Google Play. Strict stores the token after verification |
| Deletion marker: SHA-256 of `google:` plus the subject, plus promotion flags | Created only when an account is deleted | Stop a used promotion or Early Adopter assignment from returning | No. The raw subject is not in the marker |

The Google ID token is sent to the Strict service to open a session and again to delete an account. The app does not store that token as the session.

Display name is returned at sign-in and stored only in the on-device profile. The server has no user-name column. Do not list the name as data Strict’s server collects. Whether the Play services sign-in library should be declared separately is a Play SDK confirmation.

## Not transmitted by Strict

Confirmed by code. Do not list these as data Strict collects or shares, unless a Play SDK disclosure says the library itself does.

- Workout history, plans, exercise catalog, sets, reps, weights, and notes, except the Founder metadata row above.
- Body weight and measurements.
- Progress photos.
- Goals and achievements.
- Health Connect readings (steps, exercise sessions, resting heart rate, HRV, sleep). The app can read them after permission. It does not write them into its database, does not send them to the Strict service, and does not put them in the ZIP.
- Theme, language, and lock-screen preference.
- The exported ZIP, after the person saves it somewhere they choose. Strict does not upload that file.

## Encryption in transit

Confirmed by code for the release app: `BuildConfig` uses `https://api.strictworkout.eu`. Answer “encrypted in transit” for data the release app sends to that API.

The debug build can use `http://127.0.0.1:8082`. That is not the Play build. Do not describe the debug loopback as the release transport.

Requires developer/legal confirmation: this inventory does not claim that the local Room database has an extra encryption layer. It is app-private storage. The session token on the phone is in encrypted storage under `noBackupFilesDir`.

## Account deletion and deletion requests

Confirmed by code:

- In the app: Settings, Delete account / Fiók törlése. The button stays disabled until the person confirms. A fresh Google sign-in is required. Local data for that account is erased only after the server returns success.
- On the server: the account, sessions, external identity, Founder rows, and promotional grant rows are deleted. The deletion marker remains. The Play purchase row is unlinked and `claim_blocked` is set. The token stays. The Play subscription is not cancelled.
- Web route in this backend build: `GET /account/delete`, with no admin login. It is not an email-only form.

Not deployed. Do not paste `https://api.strictworkout.eu/account/delete` into Play Console until that host serves this page.

Play’s Help page says a retained category must be explained. The privacy policy says the marker and the purchase token are kept and that no end date has been chosen. That is accurate. It is not a finished retention period. Requires developer/legal confirmation before you treat the Play answer as complete.

## SDKs

Confirmed by code: `app/build.gradle.kts` includes AndroidX, Compose, Room, DataStore, Health Connect, Glance, Credentials, Google Identity, OkHttp, Play Billing, and WorkManager. It does not declare Firebase, Crashlytics, Sentry, an analytics SDK, or an ads SDK.

Requires developer/legal confirmation: Play services libraries can have their own telemetry. Complete Play Console’s SDK guidance before answering “no data shared” for those libraries. Strict’s own code does not add an analytics payload.

## Security and logs

Confirmed by code: sign-in diagnosis does not log the ID token, Google subject, email, or name. Application deletion and billing paths log error codes. Purchase tokens are stored in the database, not written into those application log lines.

Requires backend/infrastructure confirmation: whether the reverse proxy, host, or database provider stores IP addresses, and how long any log is kept. Do not answer “we do not log IP addresses” from this repository.

## Privacy policy URL for the form

The pages exist in this tree:

- `GET /privacy` and `GET /privacy/en` — English
- `GET /privacy/hu` — Hungarian

They do not require a session. They contain no analytics script. The expected production URL, after this API build is deployed and the proxy forwards those paths, is `https://api.strictworkout.eu/privacy`.

`AboutConfig.privacyPolicyUrl` stays null until that host actually returns the page. The app shows the in-app notice instead of opening a dead browser link.

Requires developer/legal confirmation before the URL is submitted: controller legal name, postal address, privacy contact, data-protection officer, legal bases, hosting location, international-transfer tool, retention of the purchase token and the deletion marker, log retention, target age, and how policy changes are announced. Those items are marked TODO in the policy. The policy is not legally certified.
