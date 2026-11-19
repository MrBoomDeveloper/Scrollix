#!/usr/bin/env bash
# Builds production releases.
#
# Usage:
#   ./scripts/release.sh linux      Desktop release for Linux (run on Linux)
#   ./scripts/release.sh windows    Desktop release for Windows (run on Windows)
#   ./scripts/release.sh android    Play Store bundle + APK
#   ./scripts/release.sh all        Desktop release for the current OS, then Android
#
# Set VERSION=... to override the version taken from git.

set -euo pipefail

. "$(dirname "$0")/lib-release.sh"

here="$(cd "$(dirname "$0")" && pwd)"

usage() {
    sed -n '3,11p' "$0" | sed 's/^# \{0,1\}//'
}

run_linux() {
    "$here/release-linux.sh"
}

run_windows() {
    if [ -n "${WINDIR:-}" ] && command -v powershell >/dev/null 2>&1; then
        powershell -NoProfile -ExecutionPolicy Bypass -File "$here/release-windows.ps1"
    else
        die "Windows packages must be built on Windows: run scripts\\release-windows.bat there."
    fi
}

run_android() {
    "$here/release-android.sh"
}

target="${1:-}"

case "$target" in
    linux) run_linux ;;
    windows) run_windows ;;
    android) run_android ;;
    all)
        case "$(uname -s)" in
            Linux) run_linux ;;
            MINGW*|MSYS*|CYGWIN*) run_windows ;;
            *) info "Skipping desktop release: unsupported host $(uname -s)" ;;
        esac

        run_android
        ;;
    *)
        usage >&2
        exit 1
        ;;
esac
