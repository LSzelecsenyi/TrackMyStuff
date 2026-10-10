# Android release signing

This is the local procedure for signing Strict upload artifacts. It does not create a keystore, and it does not contain a real password. Nothing in this file is a claim that a signed App Bundle has already been built.

Package name: `com.strictworkout.app`. Version for the first upload: `versionName` `0.1.0`, `versionCode` `1`, both in `app/build.gradle.kts`. Play rejects an upload whose `versionCode` is not higher than the last accepted one. Do not lower it.

## Three different keys

| Key | Who holds it | What it signs | Google Sign-In certificate |
| --- | --- | --- | --- |
| Debug key | Android SDK, usually `%USERPROFILE%\.android\debug.keystore`, alias `androiddebugkey` | Debug builds only. Application ID `com.strictworkout.app.debug` | Debug Android OAuth client |
| Upload key | You, outside this repository | The AAB or APK you upload to Play | Only a sideloaded release build. Not the copy Play installs |
| Play App Signing key | Google, after you enroll Play App Signing | The APKs Play delivers to users | The Android OAuth client for a Play-installed app |

Do not assume the upload key and the Play App Signing key are the same. For a new app, enroll Play App Signing and let Google hold the app signing key. You keep only the upload key. A successful debug sign-in, or a sign-in from an APK you installed yourself, does not prove that a Play-installed build can sign in. Google issues the ID token only when the package name and the certificate of the installed app match an Android OAuth client.

## Create the upload key

Create the directory outside the repo, then run `keytool`. Leave the password flags off so the password is not stored in shell history. `keytool` prompts for the store password, the key password, and the certificate name.

```
mkdir %USERPROFILE%\strict-keys
keytool -genkeypair -v -keystore %USERPROFILE%\strict-keys\strict-upload.jks -alias strict-upload -keyalg RSA -keysize 2048 -validity 10000
```

Use alias `strict-upload` unless you already have a reason to pick another. The alias you type here must match `keyAlias` below. Choose a long password and store it in a password manager. The store password and key password may differ. This command is an example. Do not commit the `.jks` file, and do not paste a real password into the repo, a script, or this document.

Back up the keystore file and both passwords to a second location that is not the git repository. An offline copy plus the password manager is enough. If the upload key is lost, Play Console can reset the upload key. The app signing key stays with Google, so existing installs keep working, but you cannot upload until Google approves the new upload key. If you also registered an Android OAuth client for the old upload certificate, replace that client. The Play App Signing OAuth client does not change when only the upload key rotates.

## Configure secrets

Copy `keystore.properties.example` to `keystore.properties` in the repository root. Fill the four values. `keystore.properties` is gitignored. In a properties file, write a Windows path with forward slashes (`C:/Users/you/strict-keys/strict-upload.jks`) or escape each backslash.

```
storeFile=C:/Users/you/strict-keys/strict-upload.jks
storePassword=the-store-password
keyAlias=strict-upload
keyPassword=the-key-password
```

The lines above are placeholders. Do not commit `keystore.properties`.

Environment variables override the file. Use them in a shell that you do not save:

- `MMM_RELEASE_STORE_FILE`
- `MMM_RELEASE_STORE_PASSWORD`
- `MMM_RELEASE_KEY_ALIAS`
- `MMM_RELEASE_KEY_PASSWORD`

`local.properties` still accepts `mmm.release.storeFile`, `mmm.release.storePassword`, `mmm.release.keyAlias`, and `mmm.release.keyPassword`. Prefer `keystore.properties` so the passwords are not in the same file as the SDK path. `local.properties` is also gitignored.

`.gitignore` ignores `*.jks`, `*.keystore`, `keystore.properties`, and `signing.properties`. That is a backstop. Keep the real keystore outside the repo anyway.

Gradle does not print these passwords. `./gradlew :app:checkReleaseSigning` reports whether the four values are present and whether the file exists. It does not create a keystore.

## Build

From the repository root:

```
.\gradlew :app:checkReleaseSigning
.\gradlew :app:bundleRelease
.\gradlew :app:assembleRelease
```

`bundleRelease` is the Play upload. When the upload key is configured, the App Bundle is:

`app/build/outputs/bundle/release/app-release.aab`

The signed release APK, for a sideload check, is:

`app/build/outputs/apk/release/app-release.apk`

If signing values are missing, those two commands stop and name the missing fields. They do not fall back to the debug keystore. `app-release-unsigned.apk` or an unsigned `.aab` is not ready for Play.

These commands do not need the upload key:

```
.\gradlew :app:assembleDebug
.\gradlew :app:compileReleaseKotlin
.\gradlew :app:testDebugUnitTest
```

Release packaging also requires the Web OAuth client ID in `strict.google.serverClientId` or `STRICT_GOOGLE_SERVER_CLIENT_ID`. The build does not print that ID. Debug builds compile without it and refuse sign-in until it is set.

## Check the artifact

After a signed bundle exists, confirm the signature is your upload key. `jarsigner` prompts for nothing if the bundle is already signed. It prints the certificate. Do not expect Google’s app signing certificate inside the file you upload. Play replaces the signature after upload.

```
jarsigner -verify -verbose -certs app\build\outputs\bundle\release\app-release.aab
```

Fingerprints of the upload key, for the sideload OAuth client:

```
keytool -list -v -keystore %USERPROFILE%\strict-keys\strict-upload.jks -alias strict-upload
```

`keytool` prompts for the store password. Copy SHA-1 and SHA-256 from the output. Do not put `-storepass` on the command line.

Confirm the release build itself before upload:

- Application ID `com.strictworkout.app`, with no `.debug` suffix
- `versionCode` 1 and `versionName` 0.1.0 for the first upload
- `targetSdk` 37
- API base URL `https://api.strictworkout.eu`
- Release source set uses real billing, real Pro Discovery, real Welcome Back, and no entitlement override
- Main manifest does not set `usesCleartextTraffic`. That flag is only in the debug manifest

The string `http://127.0.0.1:8082` still exists in source as the debug default. Release traffic uses `BuildConfig.STRICT_API_BASE_URL`. R8 is off, so that debug constant can remain inside the release dex. It is not the release base URL.

## Play Console

1. Create the app with package name `com.strictworkout.app`.
2. Enroll Play App Signing. Google creates or holds the app signing key. You upload with the upload key only.
3. Open Internal testing, create a release, and upload `app-release.aab`.
4. After the first upload, open Test and release, App integrity, App signing. Copy SHA-1 and SHA-256 for the **App signing key certificate**. That is the certificate inside installs from Play. The **Upload key certificate** on the same page is the key you created above.

Upload-key reset, if the upload key is lost or leaked: Play Console, App integrity, App signing, request an upload key reset. Google confirms it. Later uploads use the new upload key. Users do not reinstall, because the app signing key did not change.

## Google Sign-In OAuth clients

Create these in the same Google Cloud project. The Web client ID is the token audience. Android clients only tell Google which package and certificate may request a token.

| Client | Package | SHA-1 |
| --- | --- | --- |
| Web application | none | none. Copy this client ID |
| Android, debug | `com.strictworkout.app.debug` | Debug keystore, alias `androiddebugkey` |
| Android, upload key | `com.strictworkout.app` | SHA-1 from `keytool -list` on the upload keystore |
| Android, Play App Signing | `com.strictworkout.app` | SHA-1 of the App signing key certificate in Play Console |

Put the Web client ID in both places:

- Android: `strict.google.serverClientId` or `STRICT_GOOGLE_SERVER_CLIENT_ID`
- Backend production: `STRICT_GOOGLE_CLIENT_ID`

The upload-key Android client matters when you install a release APK yourself. The Play App Signing Android client matters for Internal testing and production installs. Register that one after Play Console shows the app signing certificate. Until it is registered, a Play-installed build can fail sign-in even though a locally signed release build succeeds.
