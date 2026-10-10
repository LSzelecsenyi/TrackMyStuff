# Suggested Google Play listing for Strict

This is a draft for the developer to edit. It describes behavior that exists in the current app. It does not claim that paid Google Play purchases have been verified, and it does not claim a cloud copy of the workout log.

Capture screenshots, the feature graphic, and the store icon from a real build. Do not publish invented screens.

## Store listing

| Field | Suggested value |
| --- | --- |
| App name | Strict |
| Package name | `com.strictworkout.app` |
| Default language | English (en-US). Hungarian strings exist in the app. |
| Short description | Log workouts, plans, and progress on your phone. |
| Category | Health & Fitness |
| Contact email | The in-app feedback draft uses `laszlo.szelecsenyi@gmail.com`. Confirm that this is the Play Console contact before submitting. |
| Privacy policy URL | In this tree: `GET /privacy` (English), `/privacy/en`, and `/privacy/hu`. Expected production URL after the API deploy: `https://api.strictworkout.eu/privacy`. Do not enter it in Play Console until that host returns the page. The in-app notice is what the app shows until then. |
| Website | Not set in the project. |

Short description length: 48 characters. Play’s limit is 80.

## Full description

Strict is a workout log that stays on your phone.

Record plans, sets, body weight, measurements, and progress photos. See your training on a calendar and a muscle heatmap, follow weekly goals, and keep achievements as you train. You can export a ZIP backup and restore it later.

Sign in with Google is required. It keeps Founder access and Pro access with your account. Strict does not upload your workout log, body weight, measurements, or progress photos as a cloud copy.

Optional Health Connect access can show recent steps, exercise, resting heart rate, heart-rate variability, and sleep. Those readings stay in Health Connect. Strict does not send them to its own service.

Some features are part of Strict Pro, including more than three workout plans, more than five custom exercises, scheduling, longer history, progress photos, and workout import. Pro can also come from the Founding Tester program or from a limited promotional grant. Paid subscription checkout is not available in this build.

The app includes English and Hungarian.

## What not to write in the listing

- Do not say workouts sync to the cloud.
- Do not say users can buy Strict Pro in the app until a real Play purchase has been tested and the subscription product is configured.
- Do not say the app includes analytics, ads, or crash reporting.
- The in-app deletion flow and the web page in section 13 of the readiness report exist in this tree. Do not advertise the deletion URL until that backend build is deployed and the page loads.
- Do not describe Health Connect data as stored by Strict.

## Release notes for the first internal test

```text
First internal test of Strict.

Sign in with Google, create a workout plan, and log a workout on this device. Backup and restore use a ZIP file you choose. Paid subscriptions are not available in this build.
```
