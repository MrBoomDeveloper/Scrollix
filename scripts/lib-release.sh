#!/usr/bin/env bash
# Shared helpers for the release scripts. Source this file, do not run it.

set -euo pipefail

RELEASE_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RELEASE_DIST="$RELEASE_ROOT/dist"

# Artifacts are located by modification time, so anything produced before this
# script started (for example a leftover jar from a local build) is ignored.
RELEASE_MARKER="$(mktemp)"
sleep 1

info() {
    printf '==> %s\n' "$*"
}

die() {
    printf 'error: %s\n' "$*" >&2
    exit 1
}

require_project_files() {
    [ -f "$RELEASE_ROOT/local.properties" ] || die \
        "local.properties is missing. Copy local.properties.example and fill in your Telegram API credentials."
}

# Prints the version used in artifact names. Override with VERSION=... to name a
# build explicitly, otherwise the current git tag/commit is used.
release_version() {
    local version="${VERSION:-}"

    if [ -z "$version" ]; then
        version="$(git -C "$RELEASE_ROOT" describe --tags --always --dirty 2>/dev/null || true)"
    fi

    if [ -z "$version" ]; then
        version="$(date -u +%Y%m%d-%H%M)"
    fi

    printf '%s' "$version" | tr '/' '-'
}

# Returns the newest file under build/ matching the given find expression, or
# nothing when the build produced no such file. Artifacts written during this run
# win; otherwise the newest existing match is used, because an up-to-date build
# leaves its previous output untouched.
pick_artifact() {
    local newest

    newest="$(find "$RELEASE_ROOT/build" -type f -newer "$RELEASE_MARKER" "$@" 2>/dev/null |
        sort | tail -n 1 || true)"

    if [ -z "$newest" ]; then
        newest="$(find "$RELEASE_ROOT/build" -type f "$@" -exec ls -t {} + 2>/dev/null |
            head -n 1 || true)"
    fi

    printf '%s' "$newest"
}

# Creates a clean staging directory and prints its path.
stage_dir() {
    local name="$1"
    local dir="$RELEASE_DIST/$name"

    rm -rf "$dir"
    mkdir -p "$dir"
    printf '%s' "$dir"
}

checksum_file() {
    local file="$1"
    local name
    name="$(basename "$file")"

    if command -v sha256sum >/dev/null 2>&1; then
        (cd "$(dirname "$file")" && sha256sum "$name" >"$name.sha256")
    else
        (cd "$(dirname "$file")" && shasum -a 256 "$name" >"$name.sha256")
    fi
}
