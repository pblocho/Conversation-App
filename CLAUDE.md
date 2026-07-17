# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project overview

Kotlin Multiplatform voice-conversation app (Android, iOS, Desktop/JVM) with a Compose Multiplatform UI. It records microphone audio, streams it to a speech-to-text (STT) service, and plays back audio from a text-to-speech (TTS) service. Both services are **external backends** reached over gRPC — STT on port 8001, TTS on port 8000 (see `core/.../AppConfig.kt`). They are not part of this repo and must implement the contracts in `core/src/commonMain/proto/{stt,tts}.proto`.

## Commands

```bash
# Run
./gradlew :app:desktopApp:run                      # desktop app (hot reload: :app:desktopApp:hotRun --auto)
./gradlew :app:androidApp:assembleDebug            # Android APK
./gradlew :server:run                              # Ktor server stub (port 8080, not the STT/TTS backend)
# iOS: open app/iosApp in Xcode and run from there

# Compile-check all targets of a module (fast feedback for common-code changes)
./gradlew :core:compileKotlinJvm :core:compileAndroidMain :core:compileKotlinIosSimulatorArm64 :core:compileKotlinIosArm64

# Tests
./gradlew :core:jvmTest                            # includes the gRPC smoke test (starts an in-process GrpcServer)
./gradlew :core:jvmTest --tests "com.pibi.conversation.networking.SttGrpcSmokeTest"   # single test class
./gradlew :app:shared:testAndroidHostTest :app:shared:jvmTest :app:shared:iosSimulatorArm64Test
./scripts/android_e2e.sh                           # Android E2E (needs a booted emulator; starts fake gRPC backend + adb reverse)

# Proto codegen (runs automatically before compile; manual trigger:)
./gradlew :core:bufGenerateCommonMain              # output: core/build/protoBuild/generated/
```

## Module structure

Dependency chain: `core` → `app:shared` → `app:androidApp` / `app:desktopApp` / `app/iosApp` (Xcode project consuming the shared framework).

- `core` — all business logic: networking, audio, conversation state. KMP targets: `jvm`, `androidLibrary`, `iosArm64`, `iosSimulatorArm64`.
- `app:shared` — Compose UI + `ConversationViewModel`, shared across all app targets.
- `server` — unrelated Ktor hello-world stub; do not confuse it with the real STT/TTS backends.

## Data flow (the big picture)

`ConversationViewModel` (app:shared) constructs `ConversationManager(ConversationRepository(), viewModelScope)` and calls `startConversation()`. Everything downstream lives in `core`:

- `ConversationManager` is a single class in commonMain (platform differences live behind the `AudioRecorder`/`AudioPlayer` expects) orchestrating mic → STT → (transcripts → UI state + TTS). It exposes `ConversationUiState` via a `stateIn`-derived `StateFlow` (hence the injected `CoroutineScope`); internal state is a `MutableConversationUiState` mapped to immutable snapshots.
- `ConversationRepository` (commonMain, `data/repository`) is the data-source layer per the Android repository pattern: it wraps the two gRPC clients and is the only thing that should touch them directly.
- `AudioRecorder` (`expect object`) emits `Flow<ByteArray>` of audio chunks. **An empty `ByteArray` means "utterance ended"** — `SttClient` translates it into an `AudioChunk` with `end_of_utterance = true` and suppresses repeated markers until audio resumes.
- `SttClient` / `TtsClient` (commonMain) are gRPC bidirectional-streaming clients with plain-Flow APIs: `streamAudioToStt(Flow<ByteArray>)` + `textOutputFlow` on STT; `streamSpeech(SharedFlow<String>): Flow<SynthesizedSpeech>` on TTS (each result carries the WAV audio **and** the source text — the `text` field in `AudioData` of tts.proto). Clients do no playback; `ConversationManager` plays TTS audio and records QUESTION (recognized) / ANSWER (synthesized) messages.
- `AudioPlayer` (`expect object`) plays WAV bytes from TTS. All three platform actuals are implemented (JVM javax.sound, iOS AVAudioPlayer, Android AudioTrack); playback blocks until the chunk finishes so sequential TTS chunks don't overlap.

## gRPC stack — non-obvious constraints

Networking uses **kotlinx-rpc dev builds** (`0.11.0-grpc-189`), the only JetBrains path with iOS/Kotlin-Native gRPC support (the stable kotlinx-rpc gRPC is JVM-only). The API is experimental — pin versions, expect breaking changes on upgrade.

- Artifacts come from `https://redirector.kotlinlang.org/maven/kxrpc-grpc`, declared in `settings.gradle.kts` under both `pluginManagement` and `dependencyResolutionManagement`.
- The `grpcJava` version in `libs.versions.toml` (netty/okhttp transports) must **exactly match** the grpc-java version kotlinx-rpc bundles (1.81.0 for grpc-189). A mismatch compiles fine but fails at runtime with `AbstractMethodError`.
- The kotlinx-rpc compiler plugin is Kotlin-version-locked: when bumping `kotlin` in the catalog, a matching `<kotlinVersion>-0.11.0-grpc-N` artifact must exist in the repo above (check `kotlinx-rpc-compiler-plugin-k2/maven-metadata.xml`).
- Codegen is managed by the rpc Gradle plugin via Buf — do **not** apply the `com.google.protobuf` or Buf Gradle plugins to `core`.
- Generated API shape: services are interfaces with `fun Transcribe(message: Flow<AudioChunk>): Flow<Transcript>`; messages are built with a DSL (`AudioChunk { data = ...; endOfUtterance = true }`) that requires importing the generated `com.pibi.conversation.grpc.invoke`; proto `bytes` maps to `kotlinx.io.bytestring.ByteString` (construct with `ByteString(*byteArray)`, read with `.toByteArray()`).
- `GrpcClient` lives in `kotlinx.rpc.grpc.client` (not `kotlinx.rpc.grpc`); plaintext config is `credentials = plaintext()`.
