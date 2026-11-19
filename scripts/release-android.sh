#!/usr/bin/env bash
# Builds a production release for Android: the Play Store bundle (AAB) and a
# release APK, both copied into dist/ with SHA-256 checksums.
#
# Signing is configured in androidApp/module.yaml and androidApp/keystore.properties,
# see https://kotlin-toolchain.org/latest/user-guide/product-types/android-app
# An unsigned artifact cannot be published, so this script refuses to run without
# a keystore unless ALLOW_UNSIGNED=1 is set.

set -euo pipefail

. "$(dirname "$0")/lib-release.sh"
trap 'rm -f "$RELEASE_MARKER"' EXIT

require_project_files

if [ ! -f "$RELEASE_ROOT/androidApp/keystore.properties" ] && [ "${ALLOW_UNSIGNED:-0}" != "1" ]; then
    die "androidApp/keystore.properties is missing, so the release would be unsigned.
Create it with storeFile, storePassword, keyAlias and keyPassword, and enable
'settings: android: signing: enabled' in androidApp/module.yaml.
Set ALLOW_UNSIGNED=1 to build an unsigned artifact anyway."
fi

if [ -z "${ANDROID_HOME:-}${ANDROID_SDK_ROOT:-}" ]; then
    die "ANDROID_HOME or ANDROID_SDK_ROOT must point to an installed Android SDK."
fi

cd "$RELEASE_ROOT"

VERSION="$(release_version)"

info "Building Android release $VERSION"
./kotlin package -m androidApp
./kotlin build -m androidApp -p android -v release

mkdir -p "$RELEASE_DIST"

aab="$(pick_artifact -name '*.aab')"
if [ -n "$aab" ]; then
    target="$RELEASE_DIST/scrollix-$VERSION-android.aab"
    cp "$aab" "$target"
    checksum_file "$target"
    info "Bundle: $target"
else
    printf 'warning: no .aab was produced by ./kotlin package -m androidApp\n' >&2
fi

apk="$(pick_artifact -name '*.apk')"
if [ -n "$apk" ]; then
    target="$RELEASE_DIST/scrollix-$VERSION-android.apk"
    cp "$apk" "$target"
    checksum_file "$target"
    info "APK: $target"
else
    printf 'warning: no .apk was produced by ./kotlin build -m androidApp\n' >&2
fi

[ -n "${aab}${apk}" ] || die "Android release produced no artifacts; check the build output above."
