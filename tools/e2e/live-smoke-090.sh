#!/usr/bin/env bash
# Live end-to-end smoke test of the Pinakes 0.9.0 staff areas (Mobile API 1.10.0): the real app on
# an emulator against a real Pinakes instance, driven like a librarian.
#
#   PINAKES_E2E_URL=http://10.0.2.2:8081 PINAKES_E2E_EMAIL=<admin> PINAKES_E2E_PASSWORD=... \
#   PINAKES_E2E_DB_NAME=... PINAKES_E2E_DB_USER=... PINAKES_E2E_DB_PASSWORD=... PINAKES_E2E_DB_SOCKET=... \
#   PINAKES_E2E_MARC=/path/to/small.mrc PINAKES_E2E_SHOTS=/tmp/shots \
#   ANDROID_SERIAL=emulator-5554 tools/e2e/live-smoke-090.sh [--install]
#
# --install builds and installs the debug APK first. The device is a disposable test device: the
# app's data is cleared at the start and at the end. The database access is used for fixtures
# (reader, staff account, an overdue loan) and to remove everything the run created. Module
# switches and the daily fine changed for the run are restored. See tools/e2e/live_smoke_090.py.
set -euo pipefail
cd "$(dirname "$0")/../.."

for var in PINAKES_E2E_URL PINAKES_E2E_EMAIL PINAKES_E2E_PASSWORD PINAKES_E2E_DB_NAME PINAKES_E2E_DB_USER; do
  if [ -z "${!var:-}" ]; then echo "missing environment variable $var" >&2; exit 2; fi
done
command -v adb >/dev/null || { echo "adb not on PATH" >&2; exit 2; }
command -v python3 >/dev/null || { echo "python3 not on PATH" >&2; exit 2; }
command -v mysql >/dev/null || { echo "mysql client not on PATH" >&2; exit 2; }
command -v php >/dev/null || { echo "php not on PATH (used to hash the fixture passwords)" >&2; exit 2; }
adb ${ANDROID_SERIAL:+-s "$ANDROID_SERIAL"} get-state >/dev/null

if [ "${1:-}" = "--install" ]; then
  ./gradlew installDebug --console=plain -q
fi
adb ${ANDROID_SERIAL:+-s "$ANDROID_SERIAL"} shell pm path com.pinakes.app >/dev/null || {
  echo "com.pinakes.app is not installed on the device (use --install)" >&2; exit 2; }

exec python3 -u tools/e2e/live_smoke_090.py
