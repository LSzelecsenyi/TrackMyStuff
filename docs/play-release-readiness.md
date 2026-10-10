# Strict release readiness

Date of this audit: 9 October 2026.

This report is based on the current working tree, not on a claim that the tree has been published. Nothing in this audit was committed, pushed, or deployed. Google Play Billing has not been verified with a real purchase.

## 1. Executive summary

Strict is not ready for a public Google Play release.

The Android release configuration is pointed at the right package name and a HTTPS API host, and debug-only Pro, billing, and promotion switches are kept off the release classpath. The fresh-install crash that happened when the first workout plan opened is fixed in the current code, and the regression test for that handoff passed in this session.

A public listing is still blocked by the missing upload key and by deployment of this backend. Release signing is wired and fails closed: `bundleRelease` and `assembleRelease` do not produce a Play upload until `keystore.properties` or the `MMM_RELEASE_*` variables are set. See `docs/android-release-signing.md`. The privacy policy and the account-deletion page are implemented in this tree. Neither is live until this backend build is deployed. Play-installed sign-in also depends on the Google Play App Signing certificate, which is different from a successful debug sign-in. Paid Pro cannot be offered until Play products, the Developer API, and a real purchase test exist. The release build compiled here is unsigned, and its billing product list is empty.

## 2. Current release readiness

| Area | Status |
| --- | --- |
| Application ID, SDK levels, release API host | Meets the checked requirements |
| Debug Pro and fake billing in release | Disabled in release source sets |
| Fresh-install first plan crash | Fixed in code; automated test passed; not re-run on a device in this session |
| Release signing | Fail-closed configuration is in the project. No upload key is on this machine, so no signed AAB was produced |
| Google Sign-In on a Play-installed build | External certificate setup still required |
| Privacy policy URL | Written in this tree. Not hosted. Do not enter the URL until the public host serves it |
| Account deletion | Implemented in this tree. The public page is not deployed |
| Play Billing | Code and unit tests exist. Real purchase test has not been done |
| Backend production deploy | Not done in this audit. Production profile has no database defaults |

Separate the evidence below as follows:

- **Verified in this session:** unit tests listed in section 8, debug and release APK compilation, the merged release manifest, and the release `BuildConfig` API URL.
- **Code inspection:** behavior described from source. It was not re-run on a phone unless a test is named.
- **Still manual:** device install from Play, sign-in with the Play signing certificate, a real purchase, and backup restore on a device.
- **Still external:** Play Console, Google Cloud OAuth clients, the Play Developer API, Pub/Sub, and production hosting of the policy and deletion pages that are already in this tree.

## 3. P0 blockers

### Privacy policy is written and still needs a production deploy

- **Evidence:** English and Hungarian pages are served by this API at `GET /privacy`, `GET /privacy/en`, and `GET /privacy/hu`. The in-app notice uses the same facts and is dated 10 October 2026. `AboutConfig.privacyPolicyUrl` stays `null` so a release build does not open `https://api.strictworkout.eu/privacy` before that host serves the page. The pages are not a legal certification. Controller identity, legal bases, and several retention periods are still TODO. See `docs/play-data-safety.md`.
- **Affected component:** `PrivacyPageController`, Settings, the welcome screen, Play Console listing.
- **User impact:** Play Console still needs a live HTTPS URL. Until this backend is deployed, that URL does not exist.
- **Required action:** Deploy this backend, confirm the three routes on the public host, then set `AboutConfig.privacyPolicyUrl` to `https://api.strictworkout.eu/privacy` and enter that URL in Play Console. Do not invent the controller’s legal name or a retention period the code does not implement.

### Account deletion is implemented and still needs a production deploy

- **Evidence:** Settings has Delete account. `DELETE /api/v1/account` deletes the signed-in account. `GET /account/delete` is the public page. Neither route is on a deployed host in this session.
- **Affected component:** `AccountDeletionService`, Settings, the public page.
- **User impact:** Play Console needs the live URL from section 13. Until the backend in this tree is deployed, the page is not reachable.
- **Required action:** Deploy as described in section 13. Do not enter a URL that does not serve this page.

### The upload key still has to be created

- **Evidence:** `bundleRelease` and `assembleRelease` now stop when the upload key is missing, and they name the missing fields without printing passwords. They do not sign with the debug keystore. `docs/android-release-signing.md` is the procedure. No keystore was generated in the repo.
- **Affected component:** `app/build.gradle.kts`, gitignored `keystore.properties`.
- **User impact:** Play still has nothing to upload until the developer creates the upload key and reruns `bundleRelease`.
- **Required action:** Follow `docs/android-release-signing.md`. Keep the keystore outside git. Enroll Play App Signing, then register the Play App Signing SHA-1 as its own Android OAuth client.

### Play-installed Google Sign-In is not proven

- **Evidence:** Debug builds use application ID `com.strictworkout.app.debug`. Release and Play use `com.strictworkout.app`. The ID token audience is the Web client ID in `STRICT_GOOGLE_SERVER_CLIENT_ID` / `strict.google.serverClientId`. A debug sign-in only proves the debug package and the debug certificate.
- **Affected component:** `CredentialManagerGoogleIdentityProvider`, backend `GoogleIdentityVerifier`.
- **User impact:** A build installed by Play can fail sign-in with an audience or Android-client error even though debug sign-in works.
- **Proposed fix:** Register the clients in section 10, including the Play App Signing SHA-1, before the first Play install test.

### Paid Pro cannot be sold yet

- **Evidence:** Release `BuildConfig.STRICT_BILLING_PRODUCT_IDS` is empty. `PlayBillingGateway` then reports billing as not configured. The in-app string says paid subscriptions are not available yet. No service-account file is in the repository. No real purchase was run.
- **Affected component:** `BillingProducts`, `PlaySubscriptionService`, Play Console products.
- **User impact:** The subscribe action cannot complete. Shipping a listing that says users can buy Pro would be false.
- **Proposed fix:** Finish section 11, then run one real license-tester purchase. Until that passes, keep the listing language in `docs/play-store-listing.md`.

## 4. P1 issues

### Production process must use the prod profile

`application.yml` defaults to PostgreSQL on localhost with user and password `strict`, and `strict.admin.cookie-secure` is false. `application-prod.yml` requires `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`, and `STRICT_GOOGLE_CLIENT_ID`, and it sets the admin cookie secure. Starting the jar without `SPRING_PROFILES_ACTIVE=prod` keeps the local defaults. There is no rate limit on `POST /api/v1/auth/google`. There is no database backup job in the repository.

### Purchase tokens are stored so they can be re-queried

`play_subscription.purchase_token` holds the raw token. The lookup key is its SHA-256. That is required for reconciliation. The database must not be publicly reachable, and logs must not print the token. Current billing code does not log the token.

### Real-time developer notifications were fail-open on a partial setup

Before this audit, `GooglePushAuthenticator` treated an audience without a service-account email as configured, and then accepted any Google-signed token for that audience. The endpoint now stays unconfigured until both values are set. The external Pub/Sub setup is still undone.

### Health Connect needs a Play declaration

The manifest requests steps, exercise, resting heart rate, heart-rate variability, and sleep. Play requires a health declaration and a privacy policy that describes that access. The readings are not written into Strict’s database.

### Release is not minified

`isMinifyEnabled` is false, so resource shrinking is also off. `proguard-rules.pro` has no keep rules. Turning R8 on without a release pass can break Room, billing, or sign-in. Leave it off for the first internal build, then enable it in a dedicated change.

### In-app policy and admin notes are stale

The policy text predates billing and the promotional grants. `admin/README.md` still says admin sign-in is not connected, while the backend already has admin sessions. That README is not the Android release.

### Working tree is ahead of any commit

This tree contains uncommitted product work, including Welcome Back and secret badges, plus the fixes from this audit. An artifact built from this tree includes that work. Decide the release candidate before uploading.

## 5. P2 improvements

- `versionName` is `0.1.0` and `versionCode` is `1`. That is valid for a first upload. Choose a public version when the listing goes beyond internal testing.
- `lint.checkReleaseBuilds` is true. `abortOnError` is true, so a lint error fails `assembleRelease` and `bundleRelease`. Warnings do not. There is no lint baseline file.
- No dependency-vulnerability plugin is configured. This audit did not run one.
- The debug loopback URL `http://127.0.0.1:8082` remains in the release dex because `StrictBackendUrls.DEBUG_LOOPBACK` lives in main source and R8 is off. Release traffic uses `BuildConfig.STRICT_API_BASE_URL`, which is `https://api.strictworkout.eu`. OkHttp’s public-suffix list also contains the word localhost. Neither path is the release base URL.
- Library manifests add WorkManager, Play services sign-in, billing, and credential components. Some exported receivers are limited to `android.permission.DUMP`.

## 6. Safe fixes implemented

1. **Real-time developer notifications fail closed.** `GooglePushAuthenticator` is configured only when both the push audience and the Pub/Sub service-account email are set. A half-configured endpoint returns HTTP 503 instead of accepting any token for that audience.
2. **Release packaging requires the Web OAuth client ID.** `assembleRelease` and `bundleRelease` fail if `strict.google.serverClientId` / `STRICT_GOOGLE_SERVER_CLIENT_ID` is blank. Debug builds still compile and fail closed at sign-in. This machine already has the value set, so packaging was not blocked. The value is not recorded here.
3. **Fresh-account workout regression.** `FreshAccountFirstWorkoutTest` creates a plan in an empty account database, starts the workout, reopens the database, completes the workout, reopens it again, and checks that a second account cannot see that workout.

No database was migrated, wiped, or deployed. Signing keys were not generated. Account deletion was added after this audit and is described in section 13. It was not deployed.

## 7. Files changed

- `app/build.gradle.kts`
- `app/src/test/java/app/mymusclemap/data/repository/FreshAccountFirstWorkoutTest.kt`
- `backend/src/main/java/eu/strictworkout/billing/GooglePushAuthenticator.java`
- `backend/src/test/java/eu/strictworkout/billing/GooglePushAuthenticatorTest.java`
- `docs/play-release-readiness.md`
- `docs/play-store-listing.md`

## 8. Tests executed

| Run | Result |
| --- | --- |
| `FreshAccountFirstWorkoutTest` | 2 tests, 0 failures |
| `AppCompatFirstPlanNavigationTest` | 6 tests, 0 failures |
| `testReleaseUnitTest` (Founder rules, entitlement override, Pro Discovery, Welcome Back, billing gateway) | 5 tests, 0 failures |
| `GooglePushAuthenticatorTest` | 2 tests, 0 failures |
| `PlaySubscriptionServiceTest` | 8 tests, 0 failures |

The full Android unit suite was not run. Backend integration tests that need Docker and Testcontainers were not run. A physical clean install was not repeated in this session.

The onboarding crash is the missing `NavigationEventDispatcherOwner` after `MainActivity` became an `AppCompatActivity`. `navigation-compose` reads that owner the first time `NavHost` is composed, which is the step from onboarding into the first plan. `MainActivity` now calls `initializeViewTreeOwners()` before `setContent` and again at the start of composition. The navigation test clicks through onboarding and shows “New workout plan” without that exception. The account database being empty was not the cause.

## 9. Release build status

| Check | Result |
| --- | --- |
| `assembleDebug` | Succeeded. `app/build/outputs/apk/debug/app-debug.apk` |
| `assembleRelease` | Succeeded. Unsigned `app/build/outputs/apk/release/app-release-unsigned.apk` |
| Package | `com.strictworkout.app` |
| versionCode / versionName | `1` / `0.1.0` |
| minSdk / targetSdk | 26 / 37 |
| Android Gradle Plugin | 9.3.2 |
| Play target API | As of 31 August 2026, new apps must target Android 16 (API 36) or higher. targetSdk 37 meets that floor |
| Release API URL | `https://api.strictworkout.eu` |
| Billing product IDs in the release artifact | Empty |
| Cleartext in the release manifest | Not set. Debug cleartext stays in the debug source set |
| Deep links | No browsable intent filter. The launcher activity is the only app entry |
| Android backup | `allowBackup=false`, with cloud, device-transfer, and cross-platform domains excluded |
| Signing | Not configured |
| `lintDebug` | Original audit failed: 9 errors, 239 warnings. The later lint pass is in the Lint section |

The original audit’s `lintDebug` reported 9 errors, 239 warnings, and 3 hints. `lintRelease` in that same tree shape reported 9 errors and 238 warnings. Those counts are the baseline. The fixes and the later run are in the Lint section.

An older `app-release.aab` from 4 October 2026 was already on disk. This session did not build a new bundle.

## 10. Google Sign-In production setup

Create these OAuth clients in the same Google Cloud project. The Web client ID is the token audience. Android clients exist so Google will issue a token to that package and certificate. They do not replace the Web client ID.

| Client | Package name | Certificate SHA-1 |
| --- | --- | --- |
| Web application | none | none. Copy its client ID |
| Android, debug | `com.strictworkout.app.debug` | Debug keystore SHA-1 (`~/.android/debug.keystore`, alias `androiddebugkey`) |
| Android, upload key | `com.strictworkout.app` | SHA-1 of the local upload key |
| Android, Play App Signing | `com.strictworkout.app` | SHA-1 shown in Play Console under App signing, App signing key certificate |

Put the Web client ID in both places:

- Android release: `strict.google.serverClientId` or `STRICT_GOOGLE_SERVER_CLIENT_ID`
- Backend production: `STRICT_GOOGLE_CLIENT_ID` with `SPRING_PROFILES_ACTIVE=prod`

The backend checks signature, issuer, expiry, and audience. It does not log the token, subject, or email. A blank client ID rejects every token. There is no development backdoor.

Session behavior that is implemented:

- The backend returns an opaque bearer token once and stores only its SHA-256 digest.
- The phone keeps that token in an encrypted file under `noBackupFilesDir`.
- Default lifetime is 30 days. There is no refresh token. After expiry the user signs in again.
- A phone that already signed in can open its local workouts offline until the known Pro expiry. The first sign-in needs the network.
- Sign-out revokes that one server session and clears the local active pointer. It does not delete the account database.
- Another Google account on the same phone opens `account_<userId>.db`. ZIP restore replaces only the open account’s database.

Do not treat a debug sign-in as proof that a Play install will sign in.

## 11. Play Billing setup checklist

Status words used here: **code complete**, **unit tested**, **external setup**, **real purchase still required**.

| Step | State |
| --- | --- |
| Play Billing library calls, pending purchases, and server verification | Code complete |
| Package check against `com.strictworkout.app` | Code complete, unit tested |
| Product allow-list | Code complete. The release list is empty, so verification returns a product mismatch until IDs are set |
| Purchase token owned by another user | Code complete, unit tested. Returns 409 |
| Active, canceled-but-not-expired, and grace period keep access until expiry | Code complete, unit tested |
| Pending, paused, account hold, expired, and revoked do not grant access | Code complete |
| Acknowledgement of an entitled purchase | Code complete |
| RTDN message id stored once | Code complete, unit tested |
| RTDN rejected unless audience and service-account email are both set | Code complete, unit tested |
| Hourly reconciliation of stored tokens | Code complete. Not run against Play |
| Real purchase, renewal, cancellation, and restore | Not done |

Console setup, in order:

1. Create the Play Console app for `com.strictworkout.app` and enroll in Play App Signing.
2. Create the subscription. Put its product ID in `STRICT_BILLING_PRODUCT_IDS` for both the Android release build and the backend. Several IDs are comma-separated.
3. Add the base plan. Add an offer only if the price design needs one.
4. Add license testers.
5. Upload an internal-testing bundle signed with the upload key.
6. Create a Play Developer API service account. Grant it access to this app’s financial data in Play Console. Set `STRICT_PLAY_SERVICE_ACCOUNT_FILE` on the backend. Do not commit the JSON key.
7. Create a Pub/Sub topic for real-time developer notifications. Push it to `https://api.strictworkout.eu/api/v1/billing/rtdn`.
8. Set `STRICT_PLAY_RTDN_AUDIENCE` to the push audience, and `STRICT_PLAY_RTDN_SERVICE_ACCOUNT` to the Pub/Sub service-account email. Both are required or the endpoint stays closed.
9. With a license tester, buy the subscription, confirm the backend stores it for that user, force a renewal in the license-test clock, cancel it, and restore it on a second install of the same account.
10. Confirm a token already linked to another user is rejected.

Do not describe this as production-verified until step 9 has passed.

## 12. Data Safety inventory

No analytics, crash, or advertising SDK is declared in the app’s Gradle dependencies. Google Sign-In, Play Billing, and Play services are dependencies. Their merged manifest contains Google data-transport classes and OAuth scope constants. The app’s sign-in code requests an ID token only. It does not request Drive, Gmail, or contacts. Confirm the Data safety answers for Play services itself in the Play Console SDK guidance. That confirmation is still required.

| Data | Where it stays | Sent to the Strict backend | Sent to Google | In Android backup | Deleted how |
| --- | --- | --- | --- | --- | --- |
| Google ID token | Used once at sign-in | Yes, to exchange for a session | Issued by Google | No | Not stored after the exchange |
| Opaque session token | Encrypted on device | Sent as `Authorization` on later calls | No | No. `noBackupFilesDir` | Sign-out deletes the local copy and revokes that server session |
| Session hash | Server | Stored | No | No | All of that account’s sessions are deleted with the account |
| Google subject (`sub`) | Server `external_identity` until deletion | Stored. This is the account key | Already held by Google | No | Deleted with the account. A SHA-256 marker of `google:` plus the subject remains. The subject itself is not kept |
| Verified email | Server, and a local profile copy | Stored when Google says the email is verified | Already held by Google | Local copy excluded with app data | Deleted with the account and removed from the local profile |
| Display name | Login response and local profile only | Returned at login, not written to a user column | Already held by Google | Local copy excluded | Removed from the local profile when that account is deleted |
| Workouts, plans, exercises, sets, notes | Account Room database | No, except the Founder rows below | No | Excluded | Deleted on this phone after the server deletion succeeds. Another account’s database is left in place |
| Body weight and measurements | Local database | No | No | Excluded | Same as workouts |
| Progress photos | App-private files for that account | No | No | Excluded | Deleted on this phone with the account. An exported ZIP is not deleted |
| Health Connect readings | Read from Health Connect, not saved in Strict’s database | No | Health Connect / Google Health, under the user’s Health Connect choice | Not copied by Strict | Revoking Health Connect access does not delete Strict workouts |
| Founder enrollment, workouts, feedback, and review | Server | Yes, including feedback text | No | No | Deleted with the account. Founder capacity counters are not reduced |
| Pro Discovery trial | Server | Yes. One trial record per user | No | No | Deleted. The marker records that it was used |
| Welcome Back grant | Server | Yes. Activation can include a qualifying workout id | No | No | Deleted. The marker records that it was used |
| Play purchase | Server `play_subscription` | Yes: purchase token, product id, order id, expiry, state | Play Billing and the Play Developer API | No | Unlinked from the user and marked so another account cannot claim the token. The token, order id, and state stay. The Google Play subscription is not cancelled |
| Feedback email | User’s email app | No. The app only opens a draft | The email provider, if the user sends it | No | The user controls the draft |
| Diagnostics | No Strict crash or analytics SDK | Request logs record error codes, not tokens or feedback bodies | See the Play services note above | No | Not applicable |

Android Auto Backup and device-to-device transfer are excluded in `backup_rules.xml` and `data_extraction_rules.xml`. A ZIP backup the user exports is outside the app once they save or share it. That ZIP does not include the session token, Founder grants, promotional trials, or Play subscriptions.

Items the developer must confirm before submitting Data safety:

- Whether any Play services library telemetry should be declared in addition to the table above.
- How long the unlinked Play purchase token, order id, and subscription state may be kept. No tax or accounting period is claimed here.
- How long the deletion marker may be kept. It exists so one-time promotions cannot be claimed again. No statutory period is claimed here.
- The public privacy-policy URL. The pages are in this tree at `/privacy`, `/privacy/en`, and `/privacy/hu`. The expected production URL is `https://api.strictworkout.eu/privacy` after the deploy in section 13. It is not live yet. The form-preparation inventory is `docs/play-data-safety.md`.
- The contact email already used for the in-app feedback draft.

## 13. Privacy and account deletion

Account deletion is implemented in this working tree. It has not been deployed. Do not tell Play Console that `https://api.strictworkout.eu/account/delete` already works.

### What the user does

In the app, Settings shows **Delete account** / **Fiók törlése**. That opens a confirmation screen. The delete button stays disabled until the person checks the confirmation box. The app then asks Google for a new sign-in and calls `DELETE /api/v1/account` with the session and that ID token. The body is `{ "idToken", "confirmed": true }`. It does not contain a user id.

The public page is served by the API process at `GET /account/delete`. The expected production URL, after this backend is deployed and the reverse proxy forwards that path, is:

`https://api.strictworkout.eu/account/delete`

The page explains the consequences, continues with Google, and posts `{ "idToken", "confirmed": true }` to `POST /api/v1/account/deletion`. An email address alone is rejected. The page is not the admin app and does not expose admin routes.

The same API serves the privacy policy without a session: `GET /privacy` and `GET /privacy/en` are English, and `GET /privacy/hu` is Hungarian. After this backend is deployed and the proxy forwards those paths, the expected production URL is `https://api.strictworkout.eu/privacy`. Do not tell Play Console that URL already works. The in-app notice is the copy users see until `AboutConfig.privacyPolicyUrl` is set to a host that returns the page.

If `STRICT_GOOGLE_CLIENT_ID` is blank or not a Web client id, the page explains that deletion cannot be started. It does not offer an email form.

### Backend behavior

The user id comes from the Google subject inside the verified ID token. On the authenticated route, that subject must also match the session. A session for account A cannot delete account B.

The ID token must have been issued within the last 10 minutes, and not more than 2 minutes in the future. Otherwise the response is `401 REAUTHENTICATION_REQUIRED` and nothing is deleted.

Deletion runs in one database transaction. A failure rolls it back and the client is not told that deletion succeeded. Deleting an account that is already gone, when the marker exists, returns `204` again. Two concurrent deletions of the same account settle on one deleted user and one marker.

The transaction deletes that user’s sessions, external identity, Founder application, Founder workouts, Founder feedback and review snapshot, Founder review decision, entitlement grants, Pro Discovery trial, Welcome Back grant, and account-status grants. It then deletes `app_user`. Admin users and admin sessions are not touched. Founder and Early Adopter capacity counters are not reduced. `play_rtdn_message` has no user id and stays.

### Deletion and retention

| Data | On deletion |
| --- | --- |
| `app_user`, `external_identity`, `auth_session` | Deleted. Existing bearers stop working |
| Founder application, workouts, feedback, snapshot, review decision, entitlement grants | Deleted |
| `promotional_trial`, `welcome_back_grant`, `account_status_grant` | Deleted |
| `founder_program_capacity`, `early_adopter_cohort` | Kept unchanged. These are program counters, not a person’s profile |
| `admin_user`, `admin_session` | Kept. They are operator identities, not the deleted member |
| `play_rtdn_message` | Kept. It has no user id |
| `play_subscription` | `linked_user_id` is cleared and `claim_blocked` is set. Purchase token, token hash, package, product, state, expiry, and order id stay so Play notifications can still update the row and so the token cannot be attached to another account |
| `account_deletion_marker` | Inserted. Primary key is SHA-256 of `google:` plus the Google subject. Columns are the deletion time and booleans for Founder, Pro Discovery, Welcome Back, and Early Adopter. No email, name, subject, or workout content |

No legal retention period is stated for the purchase row or the marker. Those are unresolved decisions:

- How long the purchase token and order id may be kept after the person deletes the account.
- How long the promotion marker may be kept. Without it, the same Google account could take Founder, Pro Discovery, Welcome Back, or an Early Adopter slot again, because those rules are stored on the deleted user row and the capacity counters are not rebuilt from remaining users.

### Subscriptions

Deleting the Strict account does not cancel a Google Play subscription. The app and the web page say that, and they link to Google Play subscription management. They do not say the subscription was cancelled. A later `verify` of that purchase token for a different user returns a conflict. Real-time notifications may refresh the stored state and must leave `claim_blocked` set.

### Signing in again

The same Google account can sign in later. That creates a new Strict user id. It does not restore Founder recognition, a used Pro Discovery trial, a used Welcome Back grant, Early Adopter status, workouts, or the old purchase link. If the person had not used a promotion, the marker flag for that promotion stays false and a new account may still receive it. Early Adopter is not assigned again whenever a marker exists, because the cohort counter was not returned.

### Local data

The phone deletes local data only after the server returns `204`. It deletes that account’s Room database, its progress-photo directory, and DataStore files whose names contain that user id. It removes that user id from the on-device Founder recognition and approval-celebration records. It clears the session and the active profile. Theme, language, and lock-screen preferences stay. Another account’s database and photos stay.

The founder workout outbox is not split by account, so it is deleted. Otherwise the next account could upload the deleted account’s queued events.

Android Auto Backup and device transfer are already off (`allowBackup=false`, `backup_rules.xml`, `data_extraction_rules.xml`). Deletion does not remove a ZIP the user exported, and it does not delete files outside the app.

If the server deletion succeeds and local deletion fails, the app records the user id in `noBackupFilesDir/pending_account_erasure.txt`, leaves the signed-in app, and the welcome screen explains that local data remains and can be retried. The next launch tries that cleanup again. It does not attach those files to a new account: databases are named `account_<user id>.db`.

Offline, the app says a connection is required and does not delete local data.

### Production configuration

Deploy this backend build with the production profile. `STRICT_GOOGLE_CLIENT_ID` must be the Web client id. The HTTPS proxy in front of `api.strictworkout.eu` must forward:

- `GET /account/delete`
- `POST /api/v1/account/deletion`
- `POST /api/v1/account/deletion-preview`
- `DELETE /api/v1/account`

Put `https://api.strictworkout.eu/account/delete` in the Play Console account-deletion field only after a browser can open it and a verified Google account can complete the page. This session did not deploy it.

### Manual test

1. Sign in with a test Google account that has a workout and, if possible, a Founder application.
2. Open Settings, Delete account. Confirm the button does nothing until the box is checked.
3. Turn on airplane mode and try. The app must say nothing was deleted, and the workout must still be there.
4. Turn the network back on, confirm with Google, and delete. The app must return to the welcome screen.
5. Sign in with a second Google account. The first account’s workouts must be absent.
6. Sign in again with the first Google account. The account id is new, workouts are empty, and a used promotion does not return.
7. On a computer, open the deployed `/account/delete` page, continue with Google, and delete. Confirm the server user is gone. Do not use a URL that has not been deployed.
8. If a paid subscription exists, confirm the screen says it is not cancelled and the Play subscriptions page opens. Do not expect the charge to stop.

## 14. Play Console publishing checklist

Use `docs/play-store-listing.md` for the name and descriptions.

- [ ] App name: Strict
- [ ] Short and full descriptions from that draft
- [ ] Launcher icon from `mipmap`. It exists in the project
- [ ] Feature graphic, 1024×500, captured or designed from the real brand
- [ ] Phone screenshots from a real signed-in build, including an empty account and a completed workout
- [ ] Privacy policy URL, only after `GET /privacy` works on the public host: `https://api.strictworkout.eu/privacy`
- [ ] Account deletion URL after section 13 is deployed: `https://api.strictworkout.eu/account/delete`
- [ ] Data safety form from section 12 and `docs/play-data-safety.md`
- [ ] Health declaration for the Health Connect permissions
- [ ] App access: tell reviewers that Google sign-in is required, provide a test Google account, and say that paid purchase is not available yet
- [ ] Content rating questionnaire. The app has no ads and no public social feed. Confirm the answers. Do not mark it as made for children
- [ ] Target audience. Confirm an age group that matches a Google-account app. There is no child mode in the code
- [ ] Contact email, after confirming the feedback address
- [ ] Countries. Not set in the project
- [ ] Subscription disclosure, only after a real purchase test. Until then, do not list a price
- [ ] Internal testing track, license testers, and an enrolled upload key
- [ ] Release notes from the listing draft

## 15. Recommended next implementation task

The privacy policy, the in-app notice, and the fail-closed upload-key configuration are in this tree. The next listing work is creating the upload key from `docs/android-release-signing.md`, registering the Play App Signing OAuth client, and a real billing purchase test, plus deploying this backend so `/privacy` and `/account/delete` answer on the public host. After that deploy, set `AboutConfig.privacyPolicyUrl` to `https://api.strictworkout.eu/privacy`. Do not describe either URL as live before then. Fill the controller, legal-basis, and retention TODOs before treating the policy as ready for Play Console.

## 16. Manual steps the developer must perform

1. Create the upload keystore outside the repo and follow `docs/android-release-signing.md`. Do not commit the keystore or `keystore.properties`.
2. Create the Play Console app, enroll Play App Signing, and copy the app-signing SHA-1.
3. In Google Cloud, create the Web client and the three Android clients from section 10. Set the Web client ID on the release build and on the production backend.
4. Build a signed `bundleRelease` from the tree you intend to ship. Install that exact bundle from the internal track, not a debug APK.
5. On a clean device, sign in, create the first plan, start a workout, complete it, kill the app, and sign in again. Repeat with a second Google account and confirm the first account’s workouts are absent.
6. Deploy the backend from section 13 with `SPRING_PROFILES_ACTIVE=prod`, the database environment variables, `STRICT_GOOGLE_CLIENT_ID`, and HTTPS in front of the process. Confirm `GET /account/delete`, `GET /privacy`, `GET /privacy/en`, `GET /privacy/hu`, and `GET /api/v1/health` on the public host. The proxy must forward the privacy paths as well as the deletion paths.
7. Run the manual deletion checks in section 13 on a device before any public release.
8. After step 6 succeeds, set `AboutConfig.privacyPolicyUrl` to `https://api.strictworkout.eu/privacy` and enter that URL in Play Console. Leave it null until the host returns the page.
9. Take a restorable PostgreSQL backup and write down who can restore it.
10. Complete section 11 before anyone is offered a paid subscription.
11. Fill Data safety, the health declaration, content rating, and target audience from this report. Confirm the items marked for developer confirmation.
12. Capture real screenshots. Do not draw stand-in product screens.
13. Run `.\gradlew :app:lintDebug :app:lintRelease` before upload. Both must finish with zero errors. `checkReleaseBuilds` is true, so a signed `assembleRelease` runs release lint as well.

## Lint

Fresh run on 10 October 2026, before the fixes in this section:

| Report | Result |
| --- | --- |
| `app/build/intermediates/lint_intermediate_text_report/debug/lintReportDebug/lint-results-debug.txt` | 9 errors, 239 warnings, 3 hints |
| `app/build/intermediates/lint_intermediate_text_report/release/lintReportRelease/lint-results-release.txt` | 9 errors, 238 warnings, 3 hints |

The nine errors were the same in both variants:

| Location | Lint id |
| --- | --- |
| `ActiveWorkoutNotificationPresenter.kt:78` | `MissingPermission` |
| `themes.xml:18` | `NewApi` (`windowLightNavigationBar`, API 27, minSdk 26) |
| `MainActivity.kt` lines 333, 354, 356, 358, 360, 363 | `LocalContextGetResourceValueCall` |
| `values/strings.xml` `achievements_category_hidden_gems` | `MissingTranslation` for `hu` |

Fixes now in the tree:

- `publish()` checks `POST_NOTIFICATIONS` in the same method as `notify`. API 26–32 still post without that permission. Denial still cancels the notification. `canDeliver()` is unchanged for the rest of the app.
- `windowLightNavigationBar` moved to `values-v27/themes.xml`. API 26 no longer carries the attribute. API 31+ still uses `values-v31`.
- Welcome sign-in messages are read with `stringResource` during composition.
- Hungarian category label: `Rejtett kincsek`.
- Progress photos read EXIF through `androidx.exifinterface` instead of `android.media.ExifInterface`.
- Sign-in catches `NoCredentialException` and still returns `Failed`, which is what the parent `GetCredentialException` catch already did.
- The notification-permission request is inside an API 33 check.
- Dead API 26 version checks were removed from the notification helper. minSdk is 26.

`abortOnError` stays true. `warningsAsErrors` stays false. `checkReleaseBuilds` is now true. No `lint-baseline.xml` was added.

Justified suppression, debug only: `app/src/debug/res/xml/network_security_config.xml` ignores `InsecureBaseConfiguration`. Release does not merge that file. Debug still needs cleartext for `http://127.0.0.1:8082` and `http://10.0.2.2:8082`, and a domain rule cannot name those IP addresses.

Completed run on 10 October 2026, after the fixes:

| Report | Result |
| --- | --- |
| `lintDebug` | 0 errors, 230 warnings, 3 hints. HTML: `app/build/reports/lint-results-debug.html` |
| `lintRelease` | 0 errors, 230 warnings, 3 hints. HTML: `app/build/reports/lint-results-release.html` |

Both lint tasks finished successfully. `assembleDebug` wrote `app/build/outputs/apk/debug/app-debug.apk`. `:app:testDebugUnitTest` ran 2016 tests: 16 failed, 1 skipped. The number failures compare `-0.5 kg` with `-0,5 kg` because this machine’s locale uses a decimal comma. `SettingsScreenLayoutTest.givenSendFeedbackWhenTappedThenCallbackRunsOnce` did not find the feedback row on screen. Those failures are not from the lint edits. `assembleRelease` and `bundleRelease` stopped in a few seconds because the upload key is still missing. They did not produce a Play upload.

P0, fixed in code or narrowly suppressed:

- `ExifInterface` (3). Platform EXIF on old Android has known bugs. The processor now uses AndroidX `exifinterface` 1.4.2.
- `CredentialManagerMisuse` (1). `NoCredentialException` is now caught explicitly.
- `InsecureBaseConfiguration` (1 on debug, 0 on release). Debug cleartext only. Suppressed as described above. Release has no cleartext base config.

P1, fixed:

- `InlinedApi` (1) on `POST_NOTIFICATIONS`. The request is now inside the API 33 check.

P1, left in place:

- `UnusedAttribute` (7). `localeConfig`, `enableOnBackInvokedCallback`, and widget resize attributes are ignored below the API that introduced them. They do not crash. Removing them would drop per-app language and predictive back on Android 13+, and widget sizing on Android 12+.

P2, left in place. Counts are the same on debug and release:

- `UnusedResources` (84)
- `PluralsCandidate` (64). Suggestions to use plurals, not missing translations.
- `IconLocation` (40)
- `UseKtx` (14)
- `ModifierParameter` (4)
- `VisibleForTests` (4)
- `NewerVersionAvailable` (4)
- `GradleDependency` (4)
- `ComposableNaming` (2)
- `AndroidGradlePluginVersion` (2)
- `ObsoleteSdkInt` (1). `mipmap-anydpi-v26` remains because minSdk is 26. The adaptive icon stays in that folder.

No warning baseline was written. A baseline of these warnings would hide the next real error.
