# CampusQuest Android APK Builder

This repository wraps the CampusQuest prototype in a native Android WebView and adds native NFC NDEF reading/writing. It is designed to build a **debug APK** through GitHub Actions; it is not a signed Play Store release.

## Build an APK online (no Android Studio required)

1. Extract `CampusQuest-Android.zip` on your computer.
2. Create a new **Public** GitHub repository named `CampusQuest-Android` (recommended so your existing GitHub Pages site is not disturbed).
3. In the new repository choose **Add file → Upload files**.
4. Upload the *contents* of the extracted `CampusQuest-Android` folder to the repository root. The root should show `app/`, `.github/`, `build.gradle`, `settings.gradle`, and `gradle.properties`.
5. Click **Commit changes**.
6. Open the **Actions** tab. Select **Build CampusQuest APK**. If it did not start automatically, click **Run workflow**.
7. Wait for the workflow to finish with a green check.
8. Open the successful run and download the `CampusQuest-debug-apk` artifact. Extract the downloaded ZIP; the installable file inside is `app-debug.apk`.
9. Transfer `app-debug.apk` to an Android phone and install it. Android may ask permission to install apps from the browser/file manager; allow it only for this file if you trust the source.

## NFC notes

- The phone must have NFC and NFC must be enabled.
- Use NDEF-compatible, writable NFC tags/bracelets. Some tags are read-only or do not support NDEF.
- The app uses native Android NFC inside the APK. It does not depend on browser Web NFC.
- The tag stores `CAMPUSQUEST|V1|TOKEN=...`; it does **not** write the student's NIM to the tag. The app maps the token to a profile saved locally on that device.
- This is a prototype. LocalStorage data is not synced between phones, and there is no server or automatic QRIS verification. Reading a static token does not prove the holder's identity; some tags can be copied. Use fictional student data for testing.
- For multi-device deployment, real authentication, protected backend storage, audit logs, and secure NFC tags should be implemented before real campus use.

## Project structure

- `app/src/main/assets/index.html` — CampusQuest web interface and local data model
- `app/src/main/java/id/campusquest/app/MainActivity.java` — Android WebView and NFC bridge
- `app/src/main/AndroidManifest.xml` — app metadata and NFC permission
- `.github/workflows/build-apk.yml` — automated APK build

## Version

- App name: CampusQuest
- Application ID: `id.campusquest.app`
- Minimum Android version: Android 6.0 (API 23)
- Build output: `app/build/outputs/apk/debug/app-debug.apk`
