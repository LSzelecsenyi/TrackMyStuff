# Strict Admin

Angular admin for Strict. It is a separate application from the Android Gradle project and the Spring Boot Gradle project.

## Prerequisites

- Node.js 20.19+, 22.12+, or 24.0+ (this workspace uses Node 24.12, which matches Angular 21)
- npm 11
- The Strict backend when you later connect live data

Angular 22’s CLI requires a newer Node than this machine has, so this app uses Angular 21.2, the newest stable release that runs here.

## Install

```bash
cd admin
npm install
```

## Development

```bash
npm start
```

That runs `ng serve`. The port and API proxy are set in `angular.json`, so the app opens at:

http://localhost:4021

Do not use port 4200. That port is reserved for the Puff frontend on this machine.

During development, browser calls to `/api` are proxied to the local Strict backend:

http://127.0.0.1:8082

The dev proxy is only for this Angular dev server. It does not change backend CORS.

## Production build

```bash
npm run build
```

The production bundle calls:

https://api.strictworkout.eu

Nothing in this task deploys that build.

## Project structure

- `src/styles.css` — design tokens taken from the Android theme
- `src/app/shell` — admin frame, wordmark, navigation, account placeholder, light/dark/system theme
- `src/app/founders` — `/founders` and `/founders/:applicationId`
- `src/app/api` — local and production API base URL, and the existing admin-session HTTP contract
- `src/environments` — development uses same-origin `/api` (the proxy); production uses the Strict API host
- `proxy.conf.json` — development proxy to `127.0.0.1:8082`
- `public/strict-mark.svg` and `public/favicon.svg` — the Android launcher mark, kept as SVG

## Authentication status

The backend already separates admin access from member access:

- `POST /api/v1/admin/session` with `{ "idToken": "<Google ID token>" }` creates an admin session only when the Google subject is listed in `STRICT_ADMIN_GOOGLE_SUBJECTS`
- the response is an opaque admin bearer, not a Strict user bearer
- `DELETE /api/v1/admin/session` revokes that admin session
- `/api/v1/admin/**` requires the admin authority
- the member bearer filter does not authenticate `/api/v1/admin/**`

This foundation does not sign an admin in. The account control states that sign-in is not connected, and no token or Google client id is stored in the Angular source. Founder pages do not call the review API yet.

## Intentionally not implemented

- Founder approval and rejection
- Lifetime entitlement grants
- loading real Founder review records or rendering the final analytics
- user management, billing, and generic analytics dashboards
- Android changes
- deployment, nginx, DNS, and a new production auth design

## Tests

```bash
npm test
```
