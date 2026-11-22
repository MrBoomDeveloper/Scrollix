# AGENTS.md

## Project

Scrollix is a Telegram client built with Kotlin Multiplatform and Compose
Multiplatform. A single shared Compose UI targets Android and desktop JVM.

The project uses the **Kotlin Toolchain** (Amper-style `module.yaml` files), not
Gradle. Do not add Gradle build files. Add dependencies and build settings to the
relevant module's `module.yaml`; version aliases live in `libs.versions.toml`.

## Repository layout

| Path | Purpose |
| --- | --- |
| `project.yaml` | Root module and plugin list |
| `androidApp/` | Android application (`product: android/app`, `minSdk` 30, `compileSdk`/`targetSdk` 37) |
| `desktopApp/` | Desktop JVM entry point (`desktopApp/src/main.kt`) |
| `shared/` | Kotlin Multiplatform library with all UI and business logic (Android + JVM) |
| `build-config/` | Kotlin Toolchain plugin that generates `com.mrboomdev.scrollix.generated.BuildConfig` from `local.properties` |
| `scripts/` | Release scripts (`release.sh` and platform helpers) |
| `build/` | Generated build output |
| `dist/` | Generated release artifacts |
| `tdlib_files/` | Local TDLib development artifact (gitignored) |

### Shared sources

- `shared/src/` — common code: `App.kt`, `Utils.kt`, `Platform.kt`, `data/`,
  `features/`, `ui/`.
- `shared/src@android/`, `shared/src@jvm/` — platform `actual` implementations
  for `expect` declarations such as storage, voice playback, WebM decoding,
  image decoding, file picking, QR camera access, and the desktop context menu.
- `shared/test/` — common tests.
- `shared/test@android/`, `shared/test@jvm/` — platform-specific tests.
- `shared/composeResources/` — Compose Multiplatform resources. Generated
  accessors are available through `com.mrboomdev.scrollix.resources.Res`.

### Main packages

- `shared/src/data/provider/AccountSession.kt` — UI-facing session contract.
  Screens and composables should depend on this, not on TDLib directly.
- `shared/src/data/api/` — TDLib integration:
  `TelegramAccountSession.kt` (session implementation), `TelegramProvider.kt`,
  and `ApiManager.kt` (account/session lifecycle).
- `shared/src/data/model/` — plain shared models.
- `shared/src/data/storage/` — settings, account stores, and platform storage.
- `shared/src/data/cache/`, `shared/src/data/database/` — local Room/SQLite cache.
- `shared/src/features/` — screens: `home`, `dialog`, `search`, `settings`,
  `login`, `accounts`, `profile`.
- `shared/src/ui/` — reusable components (`components/`), theme (`theme/`),
  vector icons (`icons/`), navigation (`navigation/`), adaptive layout helpers.
- `androidApp/src/` — `MainActivity.kt` and `ScrollixApplication.kt`.
- `desktopApp/src/` — desktop `main.kt`.

## Conventions

- Prefer common code in `shared/src/`. Platform-specific code belongs in
  `shared/src@<platform>/` and must implement an `expect` declaration.
- Keep tests in the matching source set: common logic in `shared/test`,
  platform behavior in `shared/test@<platform>`.
- UI reads state from `AccountSession`; avoid calling TDLib directly from
  composables.
- Use `MaterialTheme.colorScheme` for UI colors. Adaptive custom emoji
  (`needsRepainting` from TDLib sticker metadata) are tinted with
  `MaterialTheme.colorScheme.primary`; preserve that flag when adding new
  custom-emoji loading or rendering paths.
- Follow existing Compose patterns and reusable components in
  `shared/src/ui/components/` instead of introducing parallel implementations.
- Add new dependencies through the module's `module.yaml`, using aliases from
  `libs.versions.toml` where available.

## Configuration

Telegram API credentials are read from `local.properties` at build time:

```sh
cp local.properties.example local.properties
```

```properties
telegram.apiId=123456
telegram.apiHash=0123456789abcdef0123456789abcdef
```

Get credentials from <https://my.telegram.org/apps>. `local.properties` is
gitignored and must never be committed. Its values are compiled into the app
binary, so treat them as build configuration rather than high-security secrets.
After changing the file, reload the IDE or rebuild so the generated `BuildConfig`
is refreshed.

## Build and test

The `kotlin` (macOS/Linux) and `kotlin.bat` (Windows) scripts are
self-bootstrapping wrappers for Kotlin Toolchain `0.14.0`. The first invocation
downloads the toolchain and a JRE.

```sh
./kotlin build              # build all modules
./kotlin test               # run all tests
./kotlin test -m shared     # run tests for a single module
./kotlin run -m desktopApp  # run the desktop app
./kotlin run -m androidApp  # run the Android app
```

On Windows, use `kotlin.bat` instead of `./kotlin`.

Prerequisites:

- Network access on the first build, so the wrapper can fetch the toolchain.
- `local.properties` with valid Telegram API credentials.
- For Android targets, an installed Android SDK that the toolchain can discover
  (for example through `ANDROID_HOME` / `ANDROID_SDK_ROOT`) with its licenses
  accepted. Without accepted licenses, the build stops at the SDK license check
  even when shared/JVM code compiled successfully.

### Tests

Tests use `kotlin.test` and mirror the package layout of the code they cover.
Run `./kotlin test -m shared` while iterating on shared logic. Pure common tests
(parsers, formatters, history controllers) belong in `shared/test`; JVM or
Android `actual` behavior belongs in the matching platform test folder.

## Releases

Release scripts build and package artifacts into `dist/`:

```sh
./scripts/release.sh linux
./scripts/release.sh windows
./scripts/release.sh android
./scripts/release.sh all
```

Set `VERSION=...` to override the version derived from the current git
tag/commit. Windows packages must be built on Windows; Linux packages on Linux.
