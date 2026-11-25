# Ashura Agent

<img src="docs/images/ashura.png" alt="Ashura Agent avatar" width="144">

Ashura Agent is a native Android coding assistant built with Kotlin and Jetpack Compose. Open a project folder, configure a model provider, and use Chat for questions or Agent mode for workspace tasks.

## Features

| Feature | What it does |
| --- | --- |
| Chat and Agent modes | Stream responses and see workspace actions as they run. |
| Workspace tools | Read, search, create, edit, rename and delete files inside the project you select. |
| Approvals | Choose whether to auto-approve text edits. Moves, deletes and uploads request approval. |
| Model providers | Select a preset or configure your own compatible API endpoint and model. |
| Reusable skills | Use built-in guidance for code review, debugging and app or web UI work. |
| Static site deployment | Deploy a prepared static folder using a locally saved Vercel API token. |
| Local conversations | Keep chat history on the device. Requests go to the configured model provider. |

## Install on Android

Requires Android 8.0 or later.

1. Clone this repository and open it in Android Studio.
2. Install Android SDK Platform 36 and use JDK 17 for Gradle.
3. Connect your Android device with USB debugging enabled, then select it and click **Run**.
4. In Ashura, choose a project folder and configure **Model & provider** before starting a chat.

To build an APK manually, run `./gradlew :app:assembleDebug` on macOS/Linux or `.\gradlew.bat :app:assembleDebug` on Windows. The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

See the [full setup and installation guide](docs/SETUP.md) for ADB commands, first launch and tests.
