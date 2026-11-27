# AGENTS.md

## Keeping this file lean

This is an instruction sheet, not documentation or a changelog.

- Put in: commands to run, constraints that are not obvious from the code, and
  invariants a change can silently break.
- Leave out: changelogs, "what changed" notes, feature walkthroughs, restated
  code, session notes (dates, coordinates, machine details), versions that
  churn, and anything an agent learns by reading the file it is about to edit.
- When a rule stops being true, replace or delete it — do not append a
  correction. If a fact belongs to the code, put it in a comment or a test
  instead of here.

## Project

Scrollix is a Telegram client built with Kotlin Multiplatform and Compose
Multiplatform: one shared Compose UI for Android and desktop JVM.

Builds use the **Kotlin Toolchain** (Amper-style `module.yaml`), not Gradle.
Do not add Gradle files. Dependencies and build settings go into the relevant
module's `module.yaml`; version aliases live in `libs.versions.toml`.

## Quick start

```sh
cp local.properties.example local.properties   # Telegram API credentials
./kotlin build              # build all modules
./kotlin test               # all tests
./kotlin test -m shared     # fast loop for shared logic
./kotlin run -m desktopApp  # desktop app
```

On Windows use `kotlin.bat`. The fast check is `./kotlin test -m shared` plus
`./kotlin build -m desktopApp`; the Android emulator is only needed for
interaction or layout changes.

## Repository layout

| Path | Purpose |
| --- | --- |
| `project.yaml` | Root module and plugin list |
| `androidApp/` | Android application (`minSdk` 30, `compileSdk`/`targetSdk` 37) |
| `desktopApp/` | Desktop JVM entry point |
| `shared/` | Kotlin Multiplatform UI and app-shell library |
| `shared/schemas/` | Exported Room schemas used by migration tests; commit them |
| `provider/` | `AccountSession` contract and shared models |
| `contract/` | `AccountSessionContract` suite and in-memory test storage |
| `telegram/`, `max/`, `vk/` | Provider integrations |
| `build-config/` | Plugin that generates `BuildConfig` from `local.properties` |
| `scripts/` | Release scripts |
| `build/`, `dist/` | Generated build output and release artifacts |
| `tdlib_files/` | Local TDLib artifact (gitignored) |

- `shared/src/` is common code; `shared/src@android/` and `shared/src@jvm/`
  hold `actual`s for storage, media, file picking, QR camera and the desktop
  context menu.
- Tests live in `shared/test/` and `shared/test@<platform>/`.
- Resources are accessed through `com.mrboomdev.scrollix.resources.Res`.
- `AccountSession.kt` is the UI-facing contract: screens depend on it, never on
  TDLib. `ApiManager.kt` owns account/session lifecycle and must not reference a
  concrete provider. Provider entry points register through
  `<Provider>Integration.init()`.
- `shared/src/features/` holds screens, `shared/src/ui/` reusable components,
  theme, icons and navigation.

## Conventions

- Common code first; platform code lives in `shared/src@<platform>/` and
  implements an `expect` declaration.
- Tests sit in the source set matching the code they cover.
- Composables read `AccountSession`; no TDLib calls from UI.
- Use `MaterialTheme.colorScheme`. Keep the `needsRepainting` tint (`primary`)
  for adaptive custom emoji.
- Reuse `shared/src/ui/components/` instead of building parallel widgets.
- Pin dependencies in `libs.versions.toml` and reference them from
  `module.yaml`.
- **1000-line limit per file.** Split by concern (`FooScreenSections.kt`,
  `XSessionMapping.kt`) and keep helpers `internal` across the new boundary.
  A file already over the limit must be split when next touched.
- **Every destructive action is confirmed.** Deleting a chat, message, cached
  file, log, contact, account or scheduled message goes through
  `ui/components/ConfirmDeleteDialog.kt` (red confirm label, Cancel first),
  including rows of pickers that own server-side data. Removing an item from an
  unsent attachment list is the one exception: nothing is stored yet.

### Libraries over reinvention

Check <https://klibs.io> before hand-rolling a layout helper, gesture, codec or
similar utility. Adopt a library only when it supports Android + JVM, is
maintained, uses Apache-2.0/MIT/BSD and has no large native payload. Keep the
call site small and add a pinned alias to `libs.versions.toml`.

Already used: Compose Multiplatform, Navigation 3, Material 3 adaptive,
Room/SQLite, FileKit, qrose, Haze, compottie/Lottie, OkHttp, alpha-video/JavaCV,
KCEF, Human-Readable, kotlinx-datetime, kotlinx-serialization, Reorderable.

Deliberately hand-rolled — do not "fix" these: `GifEncoder` (no maintained KMP
encoder), `EmojiCatalog` (mixes system and provider emoji), `gunzip`
(`java.util.zip` on both targets), Material icon `ImageVector`s (avoid
`material-icons-extended`; generate from Material Symbols), location tile math
(one OSM tile, no map SDK), folder chip dragging (wrapping `FlowRow`, not a lazy
list), `PlatformBackHandler` (Android delegates to `BackHandler`; desktop is
empty because Escape arrives through `BackNavigation`).

## Configuration

`local.properties` (gitignored, never commit) supplies `telegram.apiId` and
`telegram.apiHash` from <https://my.telegram.org/apps>. Values are compiled
into `BuildConfig`, so rebuild after editing.

Test server: set `telegram.testServer=true` (or `SCROLLIX_TEST_SERVER=1`) and
rebuild. It enables TDLib `useTestDc`, moves storage to the `scrollix-test`
profile, and skips MAX/VK. Test DCs are public — never store real credentials or
private messages there. Register a test account through the official iOS app;
`99966XYYYY` numbers are disabled. A test build is indistinguishable from a
normal one at runtime, so install it separately.

## Build and test

The first build needs network (toolchain download) and valid
`local.properties`. Android builds need `ANDROID_HOME`/`ANDROID_SDK_ROOT` with
accepted licenses.

```sh
./kotlin test -m shared
SCROLLIX_UPDATE_GOLDENS=1 ./kotlin test -m shared   # intended visual changes only
```

- Tests use `kotlin.test`; pure logic goes in `shared/test`, platform behavior
  in `shared/test@<platform>`.
- Golden tests render real Compose components. PNGs live under
  `shared/test@jvm/ui/golden/`, mismatches land in `build/golden-actual/`. Keep
  fixtures deterministic; never golden-test animated or network content.
- Every provider session passes `contract/`'s `AccountSessionContract` through
  its own subclass (`telegram/test@jvm`, `max/test`, `vk/test`); do not
  duplicate the suite. Telegram's lives in `test@jvm` because the Android
  variant needs `tdjsonjava`.

### Interactive UI checks

Desktop clicking is unreliable on Wayland (Xwayland swallows injected pointer
events; root screenshots are black). Use the Android emulator:

```sh
adb install -r <debug APK from ./kotlin build -m androidApp>
adb shell am start -n com.mrboomdev.scrollix/.MainActivity
adb exec-out screencap -p > /tmp/screen.png
adb shell input tap <x> <y>
adb shell input swipe <x1> <y1> <x2> <y2> <duration_ms>
```

## Feature invariants

Keep these when changing related code.

**Media & cache**

- Cached media is keyed by TDLib's durable remote unique id and hex-encoded
  (ids are base64 and may contain `/+=`). Load through
  `AccountSession.loadMediaBytes(fileId, uniqueId)`; never render a bare file id
  taken from a cached message or dialog.
- `mediaCacheLimitBytes` caps cached media (0 = unlimited, default 512 MB),
  evicting oldest first via `PlatformStorage.listEntries`. Dialogs and messages
  are never evicted. "Keep media" chats protect only media referenced by their
  cached messages; protected files still count toward the limit.
- Live location uses `InputMessageLiveLocation`, never a fake period on
  `InputMessageLocation`; stopping edits it with `location = null`. Use one OSM
  tile per card, no bulk downloads.
- The location picker opens on the current position: GPS/network on Android
  (the permission is requested on first use and a denial closes the picker),
  an IP approximation on desktop. With location off it offers the system
  switch and retries once the app is resumed. There are no coordinate fields;
  the pin is dragged instead, and `isLocationSupported()` hides the whole
  entry on a device that cannot produce a location.
- `VideoPlayer` owns playback UI. Android uses `MediaPlayer` + `TextureView`,
  desktop `FFmpegFrameGrabber` + `createVoicePlayer`. Never pre-decode whole
  files.

**Sessions, auth & data**

- Providers return `Result.failure` with `SessionException` (code, retryable,
  original text), never raw exceptions.
- Session termination is server-side; the current device is never offered.
- `supportsMarkAllRead` gates the bulk action; one failing chat must not stop
  the rest.
- Last-seen privacy is server-side (`UserPrivacySettingShowStatus`). Writing
  replaces the base rule and keeps per-user/per-chat exceptions in front of it;
  never rewrite the whole list as one rule.
- Auto-delete reads `loadMessageAutoDeleteTime` before writing and is gated by
  `supportsMessageAutoDelete`.
- Data saver is local: `AppSettings.dataSaving` plus `DataUsageStore`
  (approximate, only traffic Scrollix moves). Do not call server presets.
- The "Active" account is the default for settings pickers and the QR scanner;
  the chat list aggregates every account.
- Calls are Android-only and use `openDialer` (ACTION_DIAL).
- Persisted JSON is versioned: incompatible changes bump the version, add a
  pure migration, and test it against a legacy file. Older files decode through
  defaults; a newer version is never rewritten.
- Room schemas are committed under `shared/schemas/`. Bump
  `CacheDatabaseVersion` only with an explicit migration and a vN→vN+1 test;
  cached payload compatibility is part of the schema. `migrateCachedMessage`
  upgrades rows on read.
- Backup restore uses `previewSettingsBackup`, refuses a newer
  `settingsVersion`, and imports only after the user confirms.
- Unreadable `settings.json`/`outbox.json` are quarantined as `<name>.corrupt`
  (listed in Diagnostics → Storage health), not overwritten. Prune outbox
  entries of removed accounts, but never when the account list is empty.
- Drafts live on the server (`saveDraft`/`loadDraft`, scoped by `threadId`
  for forum topics) and carry custom-emoji spans. A debounced save must not
  clear an unreadable or not-yet-loaded draft. A draft edited elsewhere reaches
  an open composer through the dialog flow and replaces it only while the local
  text still matches the last server draft (`shouldApplyRemoteDraft`); local
  typing always wins and is saved over the remote draft.

**Composer, outbox & sending**

- All sends, edits and retryable deletions go through `OutboxManager`; queued
  rows use negative local ids. Retries use backoff for retryable
  `SessionException`s and file retries reuse the original paths. Delivery is
  at-least-once — never add another retry loop. Removing an account clears its
  queue.
- Scheduled sends never enter history (`upsertMessage` ignores
  `schedulingState`, pending rows skip `scheduling`); MAX and VK report
  `supportsScheduling = false`.
- Media captions edit through `editMessageCaption`, never `editMessageText`,
  gated by `editsCaption`/`supportsCaptionEditing`. An emptied caption removes
  it; caption edits queue as `Edit` with `isCaptionEdit`.
- Attachment review is blocking: only Send and Discard, no back/outside
  dismiss and no usable composer underneath. Holding Send offers the same
  schedule/silent menu as the composer. Rows reorder by album position, and
  the caption field renders custom emoji like the composer.
- `MessageTextFormat` is the single rich-text model with UTF-16 offsets. Every
  edit calls `adjustFormatting`; Markdown/clipboard HTML is converted by
  `RichPaste.kt` only for multi-character insertions; "Plain text" drops ranges.
- The message list scrolls behind the composer; its bottom padding equals the
  measured composer height.
- `ComposerHistory` snapshots text, caret, emoji spans and formatting *before*
  each edit; every new mutation calls `recordComposerEdit`.
- Batch copy joins oldest first, skips service and blank rows, counts an album
  once, and clears the selection.
- Translation targets `AppSettings.translationLanguage` (null = device); the
  chat header translates the loaded window, and once a chat is translated,
  messages paged in or arriving later are translated as they load. Translations
  live in the screen's state and are cleared when the language changes.
- Reaction lists are incomplete when `reactionExtraCount > 0`; never present
  them as complete.
- Checklists toggle through `markChecklistTasksAsDone` only when
  `canMarkTasksAsDone` allows it, and `updateMessageContent` must re-map the
  checklist or the card never refreshes.

**Profiles, navigation & search**

- The fullscreen viewer closes only on its button or a backdrop tap; content
  taps are consumed. Zoom/pan stays inside the container, the pager is disabled
  while zoomed, and inactive pages reset their zoom. Captions are clamped to
  four lines.
- Accent colors come from `upgradedGiftColors`; keep default surface styling
  when empty. Own profile renders `loadOwnProfile()` through the same
  `UserProfileDetails` shape.
- Shared media pages by `fromMessageId`; rows jump with
  `Route.Dialog(messageId = …)`.
- Link previews and Telegram links go through `DeepLinkHandler`; never call
  `LocalUriHandler` for `t.me` URLs.
- Hashtags open `Route.Search` on the Posts/Messages tab so global search runs.
- Global message search fans out per account and is manual. Account, media
  type and date range (presets or typed `YYYY-MM-DD` bounds) live in one dialog
  opened from the search bar and narrow the loaded page without another
  provider call.
- A settings row needs `settingsAnchor(title)` (or `SettingsRowGroup`) and a
  matching `SettingsEntry`; that is all search needs. The open section is
  `rememberSaveable`. Accounts and the queue are `Route.Accounts`, not settings.
- `reduceMotion` disables decorative animation only (for example the slide-in
  of a just-sent message); functional feedback stays. Do not reintroduce Compose
  shared-element transitions between screens: they were removed because they
  looked wrong.

**Notifications & diagnostics**

- Notification mutes are server-side (`loadNotificationSettings`,
  `setScopeMuted`, `setDialogMuted`). OS notifications fire only for unmuted
  chats and only when the unread count grows; there is no push service once the
  process is dead.
- Diagnostics stay on device: the rotating `diagnostics/scrollix.log`, the
  in-memory log, opt-in crash/non-fatal reports and opt-in redacted protocol
  dumps. Never upload, never log secrets, message contents or tokens, and never
  enable dumps on someone else's device.

## Adding a provider

1. Create `<name>/` with a `module.yaml` depending on `provider` and `contract`.
2. Implement `AccountSession` under `<name>/src/data/api/`, mapping every
   failure to `SessionException`.
3. Add `<name>/test/<Name>SessionContractTest.kt` subclassing
   `AccountSessionContract`.
4. Register a `<Name>Integration.init()` and call it from both entry points.
5. Wire `build-config` only if the provider needs generated config.

## Releases

```sh
./scripts/release.sh linux|windows|android|all
```

`VERSION=...` overrides the git-derived version. Windows packages build on
Windows, Linux packages on Linux.
