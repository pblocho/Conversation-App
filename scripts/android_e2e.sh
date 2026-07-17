#!/usr/bin/env bash
# End-to-end test of the Android app's voice pipeline.
#
# Starts the fake STT/TTS gRPC backend on the host, maps the device's
# localhost ports back to the host (adb reverse), then runs the
# instrumented test that drives the real pipeline (mic -> STT -> TTS).
#
# Prerequisite: a booted emulator or connected device (adb devices).
set -euo pipefail
cd "$(dirname "$0")/.."

ADB="${ANDROID_HOME:-$HOME/Library/Android/sdk}/platform-tools/adb"

if ! "$ADB" get-state >/dev/null 2>&1; then
    echo "ERROR: no device/emulator connected (adb get-state failed)." >&2
    exit 1
fi

echo "==> Starting E2E gRPC test server (STT :8001, TTS :8000)..."
./gradlew :server:runE2eGrpcServer --quiet &
SERVER_PID=$!
cleanup() {
    kill "$SERVER_PID" 2>/dev/null || true
    "$ADB" reverse --remove-all 2>/dev/null || true
}
trap cleanup EXIT

for _ in $(seq 1 60); do
    if nc -z 127.0.0.1 8001 2>/dev/null && nc -z 127.0.0.1 8000 2>/dev/null; then break; fi
    sleep 1
done
if ! nc -z 127.0.0.1 8001 2>/dev/null; then
    echo "ERROR: test server did not open ports 8001/8000 in time." >&2
    exit 1
fi
echo "==> Test server is up."

echo "==> Mapping device localhost:8001/8000 to host..."
"$ADB" reverse tcp:8001 tcp:8001
"$ADB" reverse tcp:8000 tcp:8000

echo "==> Running instrumented E2E test..."
./gradlew :app:androidApp:connectedDebugAndroidTest

echo "==> E2E test PASSED."
