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

## Tests

```shell
./gradlew test
```

Integration tests start PostgreSQL 16 with Testcontainers. Docker must be running.

## CORS

No CORS policy is configured. The Android app does not need browser CORS. The future admin UI at `https://admin.strictworkout.eu` will need an explicit allowed origin. Do not allow `*`.

## Time

Absolute timestamps use UTC for JDBC sessions, Hibernate binding, and JSON. That is not a business timezone. Calendar dates and a user's enrollment zone are later work.
