# Statcast Compare Android

A local Android app that compares an MLB hitter's Statcast numbers for a selected season against:

1. League average
2. The player's Statcast-era career numbers, 2015–present

The app runs directly on the Android phone. It does **not** need a Node server or hosted website. It only needs internet access to pull live MLB/Baseball Savant data.

## What it shows

- Average exit velocity
- Average launch angle
- Hard-hit rate
- Barrel rate
- Sweet-spot rate
- xBA
- xSLG
- xwOBA
- Season PA / BBE
- Career PA / BBE
- League comparison when the Baseball Savant leaderboard CSV is available

## Easiest way to put it on your Android phone

### Option 1: Android Studio, best for your own phone

1. Install Android Studio.
2. Unzip this folder.
3. Open the folder in Android Studio.
4. Let Android Studio sync the project.
5. Plug in your Android phone.
6. Turn on Developer Options and USB debugging on the phone.
7. Press **Run** in Android Studio.

Android Studio will install the app on your phone.

### Option 2: Build an APK with GitHub Actions, best for sharing

1. Create a new GitHub repo.
2. Upload this entire folder to the repo.
3. Go to the repo's **Actions** tab.
4. Run **Build Android APK**.
5. Download the artifact named `StatcastCompare-debug-apk`.
6. Send `app-debug.apk` to the Android phone.
7. On the phone, open the APK and allow installation from that source.

This is still a local Android app. It is just not distributed through the Play Store.

## Important notes

- This is Android-only. It will not install on iPhone.
- First search can take a bit because it loads active MLB rosters.
- Full-career comparisons can take a bit because they pull each Statcast season from 2015 onward.
- Baseball Savant sometimes rate-limits or changes CSV formats. The app surfaces those errors instead of silently showing bad data.
- Career means Statcast-era career, not pre-2015 MLB career.

## Files

- `app/src/main/java/com/folettary/statcastcompare/MainActivity.java` — app logic and UI
- `.github/workflows/build-apk.yml` — automated APK build workflow
- `app/build.gradle` — Android app configuration
