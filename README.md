# RM Prep
Offline Kotlin + Jetpack Compose Android app (minSdk 26). Independent planner; not affiliated with the Royal Navy or Royal Marines.

## Build
1. Install Android Studio (Koala or newer). Open this folder; accept Gradle sync.
2. Run unit tests: `./gradlew testDebugUnitTest` (or right-click `CoreTest` > Run).
3. Debug APK: Build > Build APK(s), output in `app/build/outputs/apk/debug/`.
4. Install: copy the APK to the phone, allow "install unknown apps", tap it. Or USB debugging + Run.
5. Release: Build > Generate Signed Bundle/APK, create a keystore, choose release.

## Status
Not compiled or run in the authoring environment (no Android SDK/Gradle). Expect to fix small compile errors on first sync.
Not yet implemented: editable plan, reminders/notifications, progress charts, Room migrations (backup JSON is versioned instead).

## Build with GitHub (no computer tools needed)
Upload all files (including `.github`) to a repo > Actions tab > "Build APK" > Run workflow > open the finished run > download the `RMPrep-debug-apk` artifact (a zip containing app-debug.apk).
