# Build the APK quickly

## Android Studio path

1. Open this folder in Android Studio.
2. Wait for Gradle sync.
3. Go to **Build → Build Bundle(s) / APK(s) → Build APK(s)**.
4. Android Studio will show a link to the generated APK.

## GitHub Actions path

The repo already includes `.github/workflows/build-apk.yml`.

1. Upload this project to GitHub.
2. Open the **Actions** tab.
3. Choose **Build Android APK**.
4. Click **Run workflow**.
5. Download the `StatcastCompare-debug-apk` artifact.

## Installing on your phone

1. Send the APK to the phone.
2. Tap it from Files/Downloads.
3. Allow installation from that source when prompted.
4. Open **Statcast Compare**.
