#!/usr/bin/env bash
# Builds a production desktop release for Linux.
#
# Produces dist/scrollix-<version>-linux-x64.tar.gz containing the executable
# JAR and a launcher script, plus a SHA-256 checksum. The desktop module pulls
# Compose Desktop for the host OS, so this must run on Linux.

set -euo pipefail

. "$(dirname "$0")/lib-release.sh"
trap 'rm -f "$RELEASE_MARKER"' EXIT

[ "$(uname -s)" = "Linux" ] || die \
    "Linux packages must be built on Linux (host: $(uname -s))."

require_project_files
cd "$RELEASE_ROOT"

VERSION="$(release_version)"
NAME="scrollix-$VERSION-linux-x64"

info "Building desktop release $VERSION"
./kotlin package -m desktopApp -p jvm -v release -f executable-jar

jar="$(pick_artifact -path '*/_desktopApp_executableJarJvm/*.jar')"
[ -n "$jar" ] || die "No executable JAR was produced by './kotlin package -m desktopApp'."

stage="$(stage_dir "$NAME")"
cp "$jar" "$stage/scrollix.jar"

cat >"$stage/scrollix.sh" <<'LAUNCHER'
#!/bin/sh
# Starts Scrollix from the JAR sitting next to this script.
set -e
dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
exec java -Dfile.encoding=UTF-8 -jar "$dir/scrollix.jar" "$@"
LAUNCHER
chmod +x "$stage/scrollix.sh"

cat >"$stage/README.txt" <<README
Scrollix $VERSION

Requires a Java 17 (or newer) runtime with "java" available in PATH.

Run ./scrollix.sh to start the application.
README

archive="$RELEASE_DIST/$NAME.tar.gz"
rm -f "$archive"
tar -czf "$archive" -C "$RELEASE_DIST" "$NAME"
checksum_file "$archive"

info "Artifact: $archive"
