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

Free use of Strict does not require an account. When a feature needs a durable server identity, the client sends a Google ID token:

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

Approval, rejection, and Lifetime Pro are not implemented.

## Tests

```shell
./gradlew test
```

Integration tests start PostgreSQL 16 with Testcontainers. Docker must be running.

## CORS

No CORS policy is configured. The Android app does not need browser CORS. The future admin UI at `https://admin.strictworkout.eu` will need an explicit allowed origin. Do not allow `*`.

## Time

Absolute timestamps use UTC for JDBC sessions, Hibernate binding, and JSON. That is not a business timezone. The Founder deadline is an absolute duration, not a calendar date in a user timezone. The workout local date is client-attested calendar metadata stored with the event.
