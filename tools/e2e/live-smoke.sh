#!/usr/bin/env bash
# Live end-to-end smoke test of the Android app against a real Pinakes instance.
#
#   PINAKES_E2E_URL=http://10.0.2.2:8081 PINAKES_E2E_EMAIL=... PINAKES_E2E_PASSWORD=... \
#   ANDROID_SERIAL=emulator-5554 tools/e2e/live-smoke.sh [--install]
#
# --install builds and installs the debug APK first. The device is a disposable test device:
# the app's data is cleared at the start and at the end. Everything the run creates on the
# server (books, calendar link, device tokens) is removed again. See tools/e2e/live_smoke.py.
set -euo pipefail
cd "$(dirname "$0")/../.."

for var in PINAKES_E2E_URL PINAKES_E2E_EMAIL PINAKES_E2E_PASSWORD; do
  if [ -z "${!var:-}" ]; then echo "missing environment variable $var" >&2; exit 2; fi
done
command -v adb >/dev/null || { echo "adb not on PATH" >&2; exit 2; }
command -v python3 >/dev/null || { echo "python3 not on PATH" >&2; exit 2; }
adb ${ANDROID_SERIAL:+-s "$ANDROID_SERIAL"} get-state >/dev/null

if [ "${1:-}" = "--install" ]; then
  ./gradlew installDebug --console=plain -q
fi
adb ${ANDROID_SERIAL:+-s "$ANDROID_SERIAL"} shell pm path com.pinakes.app >/dev/null || {
  echo "com.pinakes.app is not installed on the device (use --install)" >&2; exit 2; }

exec python3 -u tools/e2e/live_smoke.py
