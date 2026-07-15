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

# Proto codegen (runs automatically before compile; manual trigger:)
./gradlew :core:bufGenerateCommonMain              # output: core/build/protoBuild/generated/
```

## Module structure

Dependency chain: `core` → `app:shared` → `app:androidApp` / `app:desktopApp` / `app/iosApp` (Xcode project consuming the shared framework).

- `core` — all business logic: networking, audio, conversation state. KMP targets: `jvm`, `androidLibrary`, `iosArm64`, `iosSimulatorArm64`.
- `app:shared` — Compose UI + `ConversationViewModel`, shared across all app targets.
- `server` — unrelated Ktor hello-world stub; do not confuse it with the real STT/TTS backends.

## Data flow (the big picture)

`ConversationViewModel` (app:shared) constructs `ConversationManager(TtsClient(), SttClient())` and feeds text into a `MutableSharedFlow<String>`. Everything downstream lives in `core`:

- `ConversationManager` is `expect/actual` per platform (`core/src/{android,ios,jvm}Main/.../manager/`) and orchestrates the pipeline, exposing `ConversationUiState` via `StateFlow`.
- `AudioRecorder` (`expect object`) emits `Flow<ByteArray>` of audio chunks. **An empty `ByteArray` means "utterance ended"** — `SttClient` translates it into an `AudioChunk` with `end_of_utterance = true` and suppresses repeated markers until audio resumes.
- `SttClient` / `TtsClient` (commonMain) are gRPC bidirectional-streaming clients. Their public API is plain Kotlin Flows (`streamAudioToStt(Flow<ByteArray>)`, `textOutputFlow`, `streamAudioFromTts(SharedFlow<String>)`) — keep it stable; the platform `ConversationManager` actuals and the ViewModel depend on it.
- `AudioPlayer` (`expect object`) plays WAV bytes from TTS. Only the JVM actual is implemented; **the Android and iOS actuals are println stubs (TODO)**.

## gRPC stack — non-obvious constraints

Networking uses **kotlinx-rpc dev builds** (`0.11.0-grpc-189`), the only JetBrains path with iOS/Kotlin-Native gRPC support (the stable kotlinx-rpc gRPC is JVM-only). The API is experimental — pin versions, expect breaking changes on upgrade.

- Artifacts come from `https://redirector.kotlinlang.org/maven/kxrpc-grpc`, declared in `settings.gradle.kts` under both `pluginManagement` and `dependencyResolutionManagement`.
- The `grpcJava` version in `libs.versions.toml` (netty/okhttp transports) must **exactly match** the grpc-java version kotlinx-rpc bundles (1.81.0 for grpc-189). A mismatch compiles fine but fails at runtime with `AbstractMethodError`.
- The kotlinx-rpc compiler plugin is Kotlin-version-locked: when bumping `kotlin` in the catalog, a matching `<kotlinVersion>-0.11.0-grpc-N` artifact must exist in the repo above (check `kotlinx-rpc-compiler-plugin-k2/maven-metadata.xml`).
- Codegen is managed by the rpc Gradle plugin via Buf — do **not** apply the `com.google.protobuf` or Buf Gradle plugins to `core`.
- Generated API shape: services are interfaces with `fun Transcribe(message: Flow<AudioChunk>): Flow<Transcript>`; messages are built with a DSL (`AudioChunk { data = ...; endOfUtterance = true }`) that requires importing the generated `com.pibi.conversation.grpc.invoke`; proto `bytes` maps to `kotlinx.io.bytestring.ByteString` (construct with `ByteString(*byteArray)`, read with `.toByteArray()`).
- `GrpcClient` lives in `kotlinx.rpc.grpc.client` (not `kotlinx.rpc.grpc`); plaintext config is `credentials = plaintext()`.
