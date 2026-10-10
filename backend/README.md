# Strict backend

Standalone Spring Boot service for Strict. It is not part of the Android Gradle build.

Local PostgreSQL listens on `127.0.0.1:5433` so it does not take Puff's usual port and is not published on every interface. The database name and user are `strict`. The password `strict` is a local default only.

Production must run with the `prod` profile. That profile has no database defaults. Set:

- `SPRING_DATASOURCE_URL`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`
- `SERVER_PORT` when the process should not use `8082`
- `SPRING_PROFILES_ACTIVE=prod`

## Start PostgreSQL

From `backend/`:

```shell
docker compose up -d
```

## Run the backend

```shell
./gradlew bootRun
```

On Windows:

```shell
gradlew.bat bootRun
```

## Health

```shell
curl http://localhost:8082/api/v1/health
```

`GET /api/v1/health` returns HTTP 200 and `{"status":"UP"}`. It does not require authentication. Deployment checks should use this path. Actuator is not on the classpath.

## Authentication

Every Strict user signs in with Google before the main app. Workouts stay on the device. Sign-in does not upload a cloud copy of the workout log.

The client sends a Google ID token:

```shell
curl -s -X POST http://localhost:8082/api/v1/auth/google \
  -H "Content-Type: application/json" \
  -d "{\"idToken\":\"<google-id-token>\"}"
```

The backend verifies that token with Google (signature, issuer, audience, expiry) and then issues its own opaque bearer session. The raw bearer token is returned once. PostgreSQL stores only its SHA-256 digest. Treat the bearer token as a secret: do not put it in URLs, logs, or source control.

```shell
curl -s http://localhost:8082/api/v1/me \
  -H "Authorization: Bearer <opaque-token>"

curl -s -X DELETE http://localhost:8082/api/v1/auth/session \
  -H "Authorization: Bearer <opaque-token>"
```

`DELETE` returns 204 and revokes only that session. A later `GET /api/v1/me` with the same token is 401.

Google audience:

- `STRICT_GOOGLE_CLIENT_ID`

Session lifetime, ISO-8601 or Spring duration, default `30d`:

- `STRICT_AUTH_SESSION_LIFETIME`

There is no refresh token. After expiry the client signs in with Google again. Logout does not affect the user's other sessions.

The local profile starts without a Google client id and then rejects every Google token. Do not add a development backdoor. Automated tests stub verification instead of calling Google. Production must set `STRICT_GOOGLE_CLIENT_ID` with the `prod` profile.

The Google subject (`sub`) is the external identity key. Email is not the account id. A normal Google account is never an admin. Admin access stays on the separate admin allowlist.

Workout history stays in an account-scoped database on the phone. Sign-out does not delete it. Another Google account on the same phone opens a different database. ZIP restore replaces only the signed-in account's open database. Founder Pro, the Pro Discovery trial, and paid subscriptions are not restored from that ZIP.

Pro Discovery activation is `POST /api/v1/promotions/pro-discovery/activate` with the session bearer. The server stores one 14-day trial per user. A legacy on-device trial can be imported once with `POST /api/v1/promotions/pro-discovery/migrate`. Those client timestamps are clamped to 14 days and are not proof of the workout history.

Welcome Back is a separate 7-day grant: `GET /api/v1/promotions/welcome-back` and `POST /api/v1/promotions/welcome-back/activate`. The session user is the only identity. The body may contain a qualifying workout id, which blocks replay of that same workout. It does not prove the 40-day gap. The phone detects that gap from local native completions. The server sets activation, a 7×24-hour expiry, and a 180-day cooldown, and it rejects a new grant while Founder enrollment is open or another Pro source is active. Dismissing the offer does not call this endpoint. Apply Flyway `V11__welcome_back_grant.sql` with the usual backend migration.

Play purchase verification is `POST /api/v1/billing/subscriptions/verify` and requires the same bearer. The user id comes from the session. A purchase token already linked to another user is rejected. Real-time developer notifications stay on `POST /api/v1/billing/rtdn` and use Google push authentication, not a user session.

A phone that already signed in can keep using its local workouts offline until the known Pro expiry. The first sign-in needs the network. Paid Pro is not extended because the device is offline.

Debug builds can still simulate Founder, Pro Discovery, Welcome Back, and billing with `strict.entitlementOverride`, `strict.debug.proDiscovery`, `strict.debug.welcomeBack`, and `strict.debug.billing` in gitignored `local.properties`. Those modes stay on the device. They do not write Founder grants, promotional trials, Welcome Back grants, or verified Play subscriptions, and they do not skip Google sign-in. Release builds ignore them. Sign-in, sign-out, offline return, account switch, trial restore, and a billing ownership mismatch are exercised with a real Google account and the existing debug billing modes. There is no production authentication bypass.

## Founder program

The backend owns Founder enrollment, qualifying-workout progress, feedback, the Tester Analytics Report, and Pending Approval. Android DataStore is not authoritative for those once a later client integration ships. This service does not approve or reject applications, and it does not grant Lifetime Pro.

Production rules are fixed in code:

- 5 qualifying workouts unlock Temporary Pro
- 10 qualifying workouts and 6 distinct workout days complete training
- the qualification window is 45 times 24 hours from the server enrollment instant
- feedback and a Tester Analytics Report are both required

There is no profile, environment variable, query parameter, or header that selects the faster debug thresholds. Tests inject a smaller rules object on the test classpath only.

`enrolledAt` and `deadlineAt` are server instants. `deadlineAt = enrolledAt + 45 * 24 hours`. A request at exactly `deadlineAt` can still qualify. A request after `deadlineAt` expires an incomplete application. `PENDING_APPROVAL` does not expire if review is slow. `GET /api/v1/founder` persists that expiry when it is due. There is no expiry job.

Every Founder route requires the opaque bearer session from `POST /api/v1/auth/google`. The user id comes from that session. Request bodies cannot choose a user, a status, or progress counters.

```shell
curl -s -X POST http://localhost:8082/api/v1/founder/enrollment \
  -H "Authorization: Bearer example-token"

curl -s http://localhost:8082/api/v1/founder \
  -H "Authorization: Bearer example-token"

curl -s -X POST http://localhost:8082/api/v1/founder/workouts \
  -H "Authorization: Bearer example-token" \
  -H "Content-Type: application/json" \
  -d "{\"workoutId\":\"11111111-1111-1111-1111-111111111111\",\"completedAt\":\"2026-06-01T12:00:00Z\",\"localDate\":\"2026-06-01\"}"

curl -s -X PUT http://localhost:8082/api/v1/founder/feedback \
  -H "Authorization: Bearer example-token" \
  -H "Content-Type: application/json" \
  -d "{\"text\":\"The rest timer was easy to miss.\"}"

curl -s -X POST http://localhost:8082/api/v1/founder/tester-report \
  -H "Authorization: Bearer example-token" \
  -H "Content-Type: application/json" \
  -d "{\"appVersion\":\"1.0.0\",\"platform\":\"android\"}"
```

Repeating enrollment returns the same application and does not move `enrolledAt` or `deadlineAt`. Repeating the same workout id with the same `completedAt` and `localDate` is a success and does not increase the count. The same id with different details is 409. The first accepted payload stays.

The workout id is a client-generated UUID. Android's local Room id is not stable across reinstalls, so it is not the idempotency key. Two users may use the same UUID. The unique key is the application plus that UUID.

Workout completion is client-attested. The backend cannot prove that a workout was created by Strict rather than Health Connect, because workouts still live on the device. The client sends an immutable event, not a counter and not an `origin` flag. The backend derives the workout count, distinct days, Temporary Pro, and status from the events it stored. Distinct days use the submitted local date. That date must be possible for `completedAt` in a real offset from UTC-12 through UTC+14. The server timezone is not used.

Feedback is accepted only after training requirements are complete. It can be replaced until the Tester Report is submitted. The report then freezes feedback, writes one immutable review snapshot, and moves an in-window application to `PENDING_APPROVAL`. An exact report retry succeeds. A retry with a different app version or platform is 409. HTTP success is the submission. There is no share sheet and no local confirmation.

`ACTIVE_PRO` and `PENDING_APPROVAL` mean Temporary Pro is active for this application. `ACTIVE_FREE` and `EXPIRED` do not. There is no general entitlement API.

Stored Founder data is the Strict user (already linked to a Google subject and optional email), enrollment and deadline instants, status timestamps, client workout UUID, completion instant, local workout date, feedback text, app version, platform, and the server-derived counts copied into the review snapshot. Feedback is free text and may contain personal information. The backend does not store an Android id, advertising id, contacts, location, Health Connect content, body measurements, or photos. Request logs record Founder error codes, not feedback or report bodies.

Approval, rejection, and Lifetime Pro are separate from the mobile Founder routes. A normal bearer token cannot approve an application or create a grant.

Admin sign-in is `POST /api/v1/admin/session` with a Google ID token. The Google subject must be listed in `STRICT_ADMIN_GOOGLE_SUBJECTS` (comma-separated). An empty list allows nobody. There is no password and no default admin. The same allowlist is checked again on every admin request, using the Google subject stored on `admin_user` when that session was created. Email is not an admin identifier. The property is bound at process start, so changing it requires a restart; after that restart, the next request for a removed subject is rejected and that `admin_session` is revoked. Putting the subject back later does not revive the revoked cookie. A new Google login is required. An empty allowlist also rejects sessions that already exist. The server stores only the SHA-256 hash of a new opaque session in `admin_session`. The raw token is returned only as an HttpOnly `STRICT_ADMIN_SESSION` cookie (`Path=/api/v1/admin`, `SameSite=Strict`, no `Domain`). The response body is empty. The `prod` profile always marks that cookie `Secure`. Local HTTP development sets `strict.admin.cookie-secure=false`.

Browser admin requests are same-origin and use CSRF. `GET /api/v1/admin/csrf` sets a readable `XSRF-TOKEN` cookie. State-changing admin requests send that value in `X-XSRF-TOKEN` and send the cookie back. Android bearer routes do not use this cookie and do not require CSRF.

`GET /api/v1/admin/session` reads the current admin session. It requires the admin cookie and does not require CSRF. A missing, expired, revoked, or no-longer-allowlisted cookie is 401. The body is `{ "email": "<verified email or null>" }`. Email is display information copied from the verified Google identity at login. It is not used to authorize the request. The body does not include the session credential, its hash, the Google subject, or the session id.

Logout is `DELETE /api/v1/admin/session` with the admin cookie and CSRF header. It revokes that server session and clears the cookie. A later login replaces any admin cookie presented with the request by revoking that session and setting a new one.

```shell
curl -si http://localhost:8082/api/v1/admin/csrf

curl -si -X POST http://localhost:8082/api/v1/admin/session \
  -H "Content-Type: application/json" \
  -H "X-XSRF-TOKEN: <csrf-token>" \
  -H "Cookie: XSRF-TOKEN=<csrf-token>" \
  -d "{\"idToken\":\"<google-id-token>\"}"

curl -s http://localhost:8082/api/v1/admin/session \
  -H "Cookie: STRICT_ADMIN_SESSION=<admin-session>"

curl -s http://localhost:8082/api/v1/admin/founder/applications \
  -H "Cookie: STRICT_ADMIN_SESSION=<admin-session>; XSRF-TOKEN=<csrf-token>"

curl -s -X POST http://localhost:8082/api/v1/admin/founder/applications/<application-id>/approval \
  -H "X-XSRF-TOKEN: <csrf-token>" \
  -H "Cookie: STRICT_ADMIN_SESSION=<admin-session>; XSRF-TOKEN=<csrf-token>"

curl -s -X POST http://localhost:8082/api/v1/admin/founder/applications/<application-id>/rejection \
  -H "Content-Type: application/json" \
  -H "X-XSRF-TOKEN: <csrf-token>" \
  -H "Cookie: STRICT_ADMIN_SESSION=<admin-session>; XSRF-TOKEN=<csrf-token>" \
  -d "{\"reason\":\"The report did not describe the training.\"}"

curl -si -X DELETE http://localhost:8082/api/v1/admin/session \
  -H "X-XSRF-TOKEN: <csrf-token>" \
  -H "Cookie: STRICT_ADMIN_SESSION=<admin-session>; XSRF-TOKEN=<csrf-token>"
```

Repeating the same decision is a success and does not create a second grant or audit row. The opposite decision is 409 `REVIEW_CONFLICT`. Only `PENDING_APPROVAL` can be decided. Approval writes `APPROVED` and one `entitlement_grant` row with source `FOUNDER_LIFETIME` in the same transaction. Rejection stores a mandatory reason and does not create a grant. The reviewer is the `admin_user` id on `founder_review_decision`.

`GET /api/v1/entitlements` is the mobile read model. `access` is `PRO` when Founder Lifetime or Temporary Founder Pro applies. Founder Lifetime is the grant row, not a flag on the user. Temporary Founder Pro still comes from `ACTIVE_PRO` or `PENDING_APPROVAL`, and it is not reported once Lifetime applies. This endpoint does not write.

`GET /api/v1/admin/founder/applications` lists pending applications. Each row has the application id, status, verified tester email, enrollment and deadline instants, pending time, and a compact qualification summary: snapshot workout count, distinct-day count, and the required counts stored with that submission. The list does not include feedback or workout observations.

`GET /api/v1/admin/founder/applications/{id}` is the review record. `application` is the current server state. `tester.email` is the verified email, when one is stored. `qualification` and `report` are the immutable Tester Report snapshot: backend-derived counts, the rule thresholds copied at submission, submission time, app version, platform, and the submitted feedback. `workouts` are the accepted qualifying events in local-date, then completion-time order. Each workout includes its client workout id and the observations stored at ingestion. `decision` is present after approval or rejection and does not rewrite the snapshot. A missing historical rule field stays null. The payload does not include the Google subject, the Strict user id, the report idempotency id, session credentials, token hashes, or workout row ids.

Approval and rejection still return that same detail resource. Their transitions are unchanged: only `PENDING_APPROVAL` can be decided, approval writes one `FOUNDER_LIFETIME` grant, and rejection stores a reason and writes no grant.

## Tests

```shell
./gradlew test
```

Integration tests start PostgreSQL 16 with Testcontainers. Docker must be running.

## CORS

No CORS policy is configured. The Android app does not need browser CORS. The admin UI is served from the same origin that proxies `/api`, so the browser does not call `api.strictworkout.eu`. Do not add credentialed CORS for the admin cookie.

## Time

Absolute timestamps use UTC for JDBC sessions, Hibernate binding, and JSON. That is not a business timezone. The Founder deadline is an absolute duration, not a calendar date in a user timezone. The workout local date is client-attested calendar metadata stored with the event.
