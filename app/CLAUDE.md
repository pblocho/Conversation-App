# CLAUDE.md — app module

Guidance for the UI/application layer. See the root `CLAUDE.md` for the overall architecture, the gRPC stack, and `core` module constraints.

## Submodules

- `app:shared` — all UI lives here: the single `App()` composable + `ConversationViewModel` (commonMain). Also produces the **static `Shared.framework`** consumed by the Xcode project.
- `app:androidApp` — thin Android entry (`MainActivity`), applicationId `com.pibi.conversation`.
- `app:desktopApp` — thin JVM entry (`MainKt`).
- `app/iosApp` — Xcode project (SwiftUI shell). `iOSApp.swift` → `ContentView` → `MainViewControllerKt.MainViewController()` (ComposeUIViewController). Its "Compile Kotlin Framework" run-script phase rebuilds and embeds the framework on **every** Xcode build — Kotlin changes need no manual Gradle step.

## UI ↔ core wiring

`ConversationViewModel` starts the whole pipeline in `init` (on `Dispatchers.IO`): it constructs `ConversationManager(ConversationRepository(), viewModelScope)` and calls `startConversation()` — one common class, no per-platform actuals; the repository wraps the gRPC clients. The voice loop feeds TTS from STT output only — there is currently no typed-text input path into the pipeline.

On Android, `MainActivity` defers `setContent { App() }` until the `RECORD_AUDIO` permission dialog resolves, because the ViewModel starts recording immediately on composition.

## iOS specifics

- **Deployment target is iOS 18.2** — iPhone 15-era simulators (iOS 17.5) refuse to install the app; use the iPhone 16 family or newer.
- `NSMicrophoneUsageDescription` is set in `iosApp/Info.plist`; without it iOS kills the app on first mic access. Grant permission in tests via `xcrun simctl privacy <udid> grant microphone com.pibi.conversation.Conversation`.
- On this machine `xcode-select` points at CommandLineTools, which breaks `xcodebuild` **and** Kotlin/Native framework linking. Prefix commands with `DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer` (or fix once with `sudo xcode-select -s ...`).
- Networking: the simulator's `127.0.0.1` reaches the Mac, so locally running STT/TTS backends work. A **physical device** needs `AppConfig.SERVER_HOST` changed to the Mac's LAN IP.

## Android specifics

- Mic + network permissions live in `app/androidApp/src/main/AndroidManifest.xml` (`RECORD_AUDIO`, `INTERNET`, plus `usesCleartextTraffic` for plaintext gRPC). Grant mic in tests via `adb shell pm grant com.pibi.conversation android.permission.RECORD_AUDIO`.
- Networking: on the **Android emulator**, `127.0.0.1` is the device itself — the host Mac is **`10.0.2.2`**, so `AppConfig.SERVER_HOST` must be changed to reach locally running backends (or use `adb reverse tcp:8001 tcp:8001` etc. to keep `127.0.0.1`). Physical devices need the Mac's LAN IP.
- **E2E test**: `./scripts/android_e2e.sh` (with a booted emulator) starts the fake gRPC backend from the `server` module (`:server:runE2eGrpcServer`), maps ports via `adb reverse`, and runs `ConversationE2eTest` — the real pipeline (AudioRecord mic → gRPC STT → UI state → TTS) against the fake backend. The emulator mic delivers silence, which becomes the end-of-utterance marker the fake STT answers.

## Commands

```bash
./gradlew :app:desktopApp:run                 # desktop (hot reload: :app:desktopApp:hotRun --auto)
./gradlew :app:androidApp:assembleDebug       # Android APK

# iOS from CLI (simulator, no signing):
cd app/iosApp && DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer \
  xcodebuild -project iosApp.xcodeproj -scheme iosApp -configuration Debug \
  -destination 'generic/platform=iOS Simulator' CODE_SIGNING_ALLOWED=NO build
# then: xcrun simctl install <udid> <DerivedData>/.../Conversation.app && xcrun simctl launch <udid> com.pibi.conversation.Conversation

# Tests
./gradlew :app:shared:testAndroidHostTest :app:shared:jvmTest :app:shared:iosSimulatorArm64Test
```
