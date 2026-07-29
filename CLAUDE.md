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

`ConversationViewModel` (app:shared) constructs `ConversationManager(GrpcConversationRepository())` and calls `startConversation()` once, in its own scope. Everything downstream lives in `core`:

- `ConversationManager` (commonMain) runs the conversation as an **endless linear state machine** over `ConversationState`: `Init → Idle → RecordingAudio → SendToStt → WaitForTextForLlm → SendTextToLlm → WaitForAudioAndTextFromLlm → Idle → …`. Nothing is ever started by hand — `startConversation()` runs turns forever, and the only control is `stop()`, which cancels the turn in flight so the next one begins at `Init → Idle → RecordingAudio` (the UI's single **Stop** button). The current state is part of `ConversationUiState`.
  - **Both gRPC streams stay open across turns and across stops** — the backend keeps the LLM chat history per `Synthesize` stream, so reconnecting per turn would erase the conversation's memory. Everything the streams produce is funneled into channels (`transcripts`, `answers`) so the machine can pick each event up in the state that expects it instead of racing the streams.
  - Because the machine is linear, the mic is only collected during `RecordingAudio`, so the assistant's own playback can't be picked up as the next question.
  - `AudioRecorder`/`AudioPlayer` are injected as constructor lambdas (defaulting to the `expect object`s) — that is what makes the machine unit-testable; see `ConversationStateMachineTest` in commonTest.
- `ConversationRepository` (commonMain, `data/repository`) is an interface — the data-source layer per the Android repository pattern — implemented by `GrpcConversationRepository`, the only thing that should touch the gRPC clients directly.
- `AudioRecorder` (`expect object`) emits `Flow<ByteArray>` of audio chunks. **An empty `ByteArray` means "silence"** — `ConversationManager` ends the utterance after `SILENT_CHUNKS_END_UTTERANCE` consecutive empty chunks (~1.3 s), then sends one empty chunk as the end marker, which `SttClient` translates into an `AudioChunk` with `end_of_utterance = true`. The STT backend applies its own hold-off on top, so short pauses never split one question into several.
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
