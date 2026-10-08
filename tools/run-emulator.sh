#!/usr/bin/env bash
set -euo pipefail

if [[ $# -eq 0 ]]; then
    printf 'Usage: %s AVD_NAME [emulator options...]\n' "$0" >&2
    exit 64
fi

sdk_dir="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [[ -n "$sdk_dir" && -x "$sdk_dir/emulator/emulator" ]]; then
    emulator_bin="$sdk_dir/emulator/emulator"
elif emulator_bin="$(command -v emulator)"; then
    :
else
    printf 'Android emulator not found. Set ANDROID_HOME or add emulator to PATH.\n' >&2
    exit 127
fi

avd_name="$1"
shift
emulator_pid=''
assertion_pid=''

cleanup() {
    if [[ -n "$assertion_pid" ]]; then
        kill "$assertion_pid" 2>/dev/null || true
        wait "$assertion_pid" 2>/dev/null || true
    fi
}

stop_emulator() {
    if [[ -n "$emulator_pid" ]]; then
        kill "$emulator_pid" 2>/dev/null || true
        wait "$emulator_pid" 2>/dev/null || true
    fi
}

trap cleanup EXIT
trap 'stop_emulator; exit 130' INT
trap 'stop_emulator; exit 143' TERM

"$emulator_bin" -avd "$avd_name" "$@" &
emulator_pid=$!

if [[ "$(uname -s)" == 'Darwin' ]]; then
    # Keep the standalone VM active even when its Qt window is occluded.
    # The assertion belongs to this PID and ends when the emulator exits.
    /usr/bin/caffeinate -i -w "$emulator_pid" &
    assertion_pid=$!
fi

wait "$emulator_pid"
