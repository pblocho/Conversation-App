#!/usr/bin/env bash
# End-to-end test of the Android app's voice pipeline.
#
# Starts the fake STT/TTS gRPC backend on the host, maps the device's localhost ports back to the
# host (adb reverse), then runs the instrumented test that drives the real pipeline.
#
# How the device reaches the host decides whether the fake backend may move ports:
#   - emulator: the app dials 10.0.2.2:8001, which qemu routes to the host's own 8001, bypassing
#     adb reverse. The fake backend must therefore own 8001/8000 on the host.
#   - handset: the app dials 127.0.0.1:8001 and only adb reverse gets it to the host, so the fake
#     backend may sit on any free port and the mapping bridges the difference.
#
# Prerequisite: a booted emulator or connected device (adb devices).
set -euo pipefail
cd "$(dirname "$0")/.."

ADB="${ANDROID_HOME:-$HOME/Library/Android/sdk}/platform-tools/adb"
SERVER_LOG="$(mktemp -t android_e2e_server)"

if ! "$ADB" get-state >/dev/null 2>&1; then
    echo "ERROR: no device/emulator connected (adb get-state failed)." >&2
    exit 1
fi

port_is_free() {
    ! lsof -nP -iTCP:"$1" -sTCP:LISTEN >/dev/null 2>&1
}

# Prefer the real ports so the mapping is a no-op; fall back if something already holds them.
pick_port() {
    for candidate in "$@"; do
        if port_is_free "$candidate"; then
            echo "$candidate"
            return 0
        fi
    done
    echo "ERROR: no free port among: $*" >&2
    return 1
}

port_holder() {
    lsof -nP -iTCP:"$1" -sTCP:LISTEN 2>/dev/null | awk 'NR==2 {print $1" (pid "$2")"}'
}

case "$("$ADB" get-serialno)" in
    emulator-*) IS_EMULATOR=true ;;
    *) IS_EMULATOR=false ;;
esac

if [ "$IS_EMULATOR" = true ]; then
    # No room to negotiate: 10.0.2.2 lands on the host's own 8001/8000.
    for port in 8001 8000; do
        if ! port_is_free "$port"; then
            echo "ERROR: port $port on this machine is held by $(port_holder "$port")." >&2
            echo "       The emulator reaches the host directly at 10.0.2.2:$port, so adb reverse" >&2
            echo "       cannot redirect it — the test would run against that backend instead of" >&2
            echo "       the fake one. Stop it first, or run the test on a handset." >&2
            exit 1
        fi
    done
    STT_PORT=8001
    TTS_PORT=8000
else
    STT_PORT="$(pick_port 8001 9001 19001)"
    TTS_PORT="$(pick_port 8000 9000 19000)"
    if [ "$STT_PORT" != "8001" ] || [ "$TTS_PORT" != "8000" ]; then
        echo "==> Ports 8001/8000 are busy; the fake backend takes $STT_PORT/$TTS_PORT instead."
    fi
fi

echo "==> Starting E2E gRPC test server (STT :$STT_PORT, TTS :$TTS_PORT)..."
./gradlew :server:runE2eGrpcServer --args="$STT_PORT $TTS_PORT" --quiet >"$SERVER_LOG" 2>&1 &
SERVER_PID=$!
cleanup() {
    kill "$SERVER_PID" 2>/dev/null || true
    "$ADB" reverse --remove-all 2>/dev/null || true
    rm -f "$SERVER_LOG"
}
trap cleanup EXIT

# Wait for *our* server to announce itself. Watching the log rather than the port matters: a
# foreign listener on the same port would otherwise look like success and the test would quietly
# run against the wrong backend.
for _ in $(seq 1 90); do
    if grep -q "E2E gRPC test server ready" "$SERVER_LOG" 2>/dev/null; then break; fi
    if ! kill -0 "$SERVER_PID" 2>/dev/null; then
        echo "ERROR: the test server exited before it was ready:" >&2
        tail -20 "$SERVER_LOG" >&2
        exit 1
    fi
    sleep 1
done
if ! grep -q "E2E gRPC test server ready" "$SERVER_LOG" 2>/dev/null; then
    echo "ERROR: the test server did not come up within 90s:" >&2
    tail -20 "$SERVER_LOG" >&2
    exit 1
fi
echo "==> Test server is up."

echo "==> Mapping device localhost:8001/8000 to host $STT_PORT/$TTS_PORT..."
"$ADB" reverse tcp:8001 tcp:"$STT_PORT" >/dev/null
"$ADB" reverse tcp:8000 tcp:"$TTS_PORT" >/dev/null

echo "==> Running instrumented E2E test..."
./gradlew :app:androidApp:connectedDebugAndroidTest

echo "==> E2E test PASSED."
