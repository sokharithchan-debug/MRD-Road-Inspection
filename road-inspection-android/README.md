# កម្មវិធីស្រង់ទិន្នន័យស្ថានភាពផ្លូវ — Android app

**នាយកដ្ឋានអភិវឌ្ឍន៍ហេដ្ឋារចនាសម្ព័ន្ធផ្លូវជនបទ · ក្រសួងអភិវឌ្ឍន៍ជនបទ**

The road condition survey app, packaged as an installable Android app (`.apk`).
The whole web app lives inside the APK, so it runs with no internet at all.

## Getting the APK

The APK is built automatically by GitHub. You do not need Android Studio.

1. Create a repository on GitHub and upload everything in this folder to it.
2. Open the **Actions** tab. The build starts on its own; if it does not,
   choose **Build APK → Run workflow**.
3. After about 3 minutes the build finishes. Download the APK from either:
   - the **Releases** page (recommended — a permanent link you can share), or
   - the **Artifacts** section at the bottom of the build page.

## Installing on a phone

1. Copy the `.apk` to the phone (Telegram, email, USB cable — anything).
2. Open it. Android will ask to allow installing apps from that source; allow it.
3. Open **ស្ថានភាពផ្លូវ** and allow location access when the first survey starts.

To update later, install the new APK over the old one. Surveys and settings are kept,
because every build is signed with the same key.

## What the app adds over the website

| | Website | App |
|---|---|---|
| Works with no signal | after the first visit | always |
| Screen stays awake while surveying | no | yes |
| Exports | browser download | saved to **Downloads/RoadInspection** and offered for sharing |
| Khmer font | downloaded once | built in |
| Install | Add to Home Screen | normal app icon |

## Updating the app

The web app is the `web/` folder — the same files that run on the website.
Replace them, raise `versionCode` and `versionName` in `app/build.gradle`,
commit, and GitHub builds a new APK.

## Layout

| Path | What it is |
|---|---|
| `web/` | The road inspection web app; becomes the APK's assets |
| `app/src/main/java/.../MainActivity.java` | The native shell: GPS permission, camera, file saving |
| `app/src/main/res/` | App icon, name, colours |
| `keystore/road-inspection.jks` | Signing key (see below) |
| `.github/workflows/build-apk.yml` | The cloud build |

## About the signing key

Android requires every app to be signed. The key in `keystore/` signs the builds so
that a new APK installs over an older one. It is fine for handing the app out inside
the department.

If you ever publish on Google Play, or if the repository is public and you would rather
the key were not, create your own key and add it as repository secrets
(`RI_KEYSTORE_PASSWORD`, `RI_KEY_ALIAS`, `RI_KEY_PASSWORD`); the build uses them
automatically when they exist. Keep that key safe — updates can only be signed with it.

## Building on your own computer instead

Open this folder in Android Studio and press Run, or from a terminal with the
Android SDK installed:

```
gradle assembleRelease
```

The APK lands in `app/build/outputs/apk/release/`.

## Notes

- Minimum Android 6.0; built against Android 14 (targetSdk 34).
- Permissions: location (chainage measurement) and vibration (tap feedback).
  There is no camera permission — photos go through the phone's own camera app.
- The app never sends anything anywhere. All data stays on the phone until you export it.
