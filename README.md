# Kotlin Multiplatform project

This project is targeting Android, Desktop (JVM), built with
the [Kotlin Toolchain](https://kotlin-toolchain.org/latest/).

- [/androidApp](./androidApp) contains the Android application.
- [/desktopApp](./desktopApp) contains the desktop (JVM) application.
- [/shared](./shared) holds the code shared across your applications — Compose UI,
  business logic, and platform-specific implementations. [src](./shared/src) is for common code; the
  sibling `src@<platform>` folders (for example `src@android`) hold code compiled only for the platform named in the
  folder.

The `kotlin` (macOS/Linux) and `kotlin.bat` (Windows) scripts in the project root are self-bootstrapping wrappers for
the Kotlin Toolchain: they download the pinned toolchain version on first use, so no separate installation is required.
Build the whole project with `./kotlin build`.

### Configuration

Telegram API credentials are read from `local.properties` at build time and generated into
`com.mrboomdev.scrollix.generated.BuildConfig`. This file is gitignored. Create it from the example and fill in your
credentials from [my.telegram.org/apps](https://my.telegram.org/apps):

```sh
cp local.properties.example local.properties
```

```properties
telegram.apiId=123456
telegram.apiHash=0123456789abcdef0123456789abcdef
```

The values are compiled into the app binary, so they are not suitable for high-security secrets. They are kept out of
version control only. On CI, create `local.properties` from secrets before running the build.

After creating or changing `local.properties`, reload the project in the IDE so the generated `BuildConfig` source is
picked up.

### Telegram login

Telegram decides how login codes are delivered. Third-party TDLib clients usually receive the code through the official
Telegram app on another signed-in device, not by SMS. Keep an official Telegram session available when signing in.

If the code does not arrive, use **Log in with QR code** and scan it from Telegram's **Settings → Devices → Link
Desktop Device** on a signed-in device. New accounts must be registered in an official Telegram app first; TDLib cannot
create a new account by itself.

### Accounts and local data

Scrollix supports multiple Telegram accounts. Accounts are persisted in `accounts.json`, and each account has its own
TDLib database and file directory. All authorized accounts are started when the app launches, so restarting the app does
not require signing in again.

Local data is stored in:

- Desktop: `~/.scrollix/`
- Android: the app's `noBackupFilesDir/scrollix/`

Android backups are disabled because the TDLib databases contain account credentials. The `tdlib_files/` directory is a
local development artifact and is gitignored.

### Running

Run each module from the command line:

- Android app: `./kotlin run -m androidApp`
- Desktop app: `./kotlin run -m desktopApp`

### Testing

Run the project's tests with `./kotlin test`, or `./kotlin test -m <module>` for a single module.

---

Learn more
about [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html), [Kotlin Toolchain](https://kotlin-toolchain.org/latest/), [Compose Multiplatform](https://kotlinlang.org/compose-multiplatform/), [Kotlin/Wasm](https://kotl.in/wasm/), [Ktor](https://ktor.io/).
