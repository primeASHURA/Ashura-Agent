# Setup and publishing

## Build locally

Use JDK 17, Android SDK Platform 36 and Gradle 8.13. Open this directory as a project in Android Studio, let Gradle sync, then select the app configuration and run it on an emulator or device. Android Studio can generate local.properties with the local SDK path.

From PowerShell, with JAVA_HOME and ANDROID_HOME pointing to your installations:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug
```

On macOS or Linux use ./gradlew with the same tasks. The debug APK is generated at app/build/outputs/apk/debug/app-debug.apk. The first build needs network access for Gradle and dependencies.

## Install with ADB

Connect the phone over USB, enable USB debugging and approve the device prompt.
From the repository root, after building the APK:

```sh
adb devices
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n ai.ashura.app/.MainActivity
```

## First launch

1. Open Ashura and choose a workspace folder.
2. Configure the model endpoint, model ID and your own API key in Model & provider.
3. Try a test workspace from Agent mode by asking it to create and read back a small text file.
4. In the debug build, enable Direct storage access in Android Settings for eligible shared-storage workspace tools. The release build uses Android's Storage Access Framework.

## Publish the source

Create an empty GitHub repository. Use its HTTPS clone URL in place of <GITHUB_REPOSITORY_URL>. Run these commands from this folder.

```sh
git init -b main
git add .
git status --short
git commit -m "Initial Ashura AI source"
git remote add origin <GITHUB_REPOSITORY_URL>
git push -u origin main
```

API credentials, local SDK paths, signed artifacts and signing keys belong outside the repository. The repository owner chooses the license and confirms redistribution rights for artwork before making the repository public.

