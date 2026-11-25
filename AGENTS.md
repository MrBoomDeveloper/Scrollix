# AGENTS.md

## Project

Scrollix is a Telegram client built with Kotlin Multiplatform and Compose
Multiplatform. A single shared Compose UI targets Android and desktop JVM.

The project uses the **Kotlin Toolchain** (Amper-style `module.yaml` files), not
Gradle. Do not add Gradle build files. Add dependencies and build settings to the
relevant module's `module.yaml`; version aliases live in `libs.versions.toml`.

## Quick start

```sh
cp local.properties.example local.properties   # fill in Telegram API creds
./kotlin build              # build all modules
./kotlin test               # run all tests
./kotlin test -m shared     # fast loop while iterating on shared logic
./kotlin run -m desktopApp  # run the desktop app
```

On Windows use `kotlin.bat`. Fast checks = unit tests + `./kotlin build -m
desktopApp`. The Android emulator is only needed for interaction/layout changes
(see *Interactive UI checks*).

## Repository layout

| Path | Purpose |
| --- | --- |
| `project.yaml` | Root module and plugin list |
| `androidApp/` | Android application (`product: android/app`, `minSdk` 30, `compileSdk`/`targetSdk` 37) |
| `desktopApp/` | Desktop JVM entry point (`desktopApp/src/main.kt`) |
| `shared/` | Kotlin Multiplatform library with all UI and app-shell logic (Android + JVM) |
| `shared/schemas/` | Exported Room cache schemas used by migration tests; commit them |
| `provider/` | Messenger contracts and plain data models (`AccountSession`, `MessengerProvider`, `ChatMessage`, …) |
| `contract/` | Shared `AccountSessionContract` test suite and in-memory storage for provider tests |
| `telegram/` | Telegram integration: `TelegramSession`, `TelegramProvider`, TDLib DTO mapping and `TelegramIntegration` |
| `max/` | MAX integration: `MaxSession`, `MaxProvider`, the OneMe JSON WebSocket transport and `MaxIntegration` |
| `build-config/` | Kotlin Toolchain plugin that generates `com.mrboomdev.scrollix.generated.BuildConfig` from `local.properties` (enabled by the `telegram` module) |
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

- `provider/src/data/provider/AccountSession.kt` — UI-facing session contract.
  Screens and composables should depend on this, not on TDLib directly.
- `provider/src/data/model/` — plain shared models used by the providers and UI.
- `telegram/src/data/api/` — TDLib integration:
  `TelegramSession.kt` (session implementation), `TelegramProvider.kt`,
  and `TelegramIntegration.kt` (registers the provider with `ProviderRegistry`).
  App entry points call `TelegramIntegration.init()` before composing `App()`.
- `max/src/network/` — MAX transport: `MaxRpc.kt` (opcode frames over the
  WebSocket, responses matched by `seq`, `cmd=0` frames delivered as pushes),
  `MaxJson.kt` (JSON accessors and phone normalization) and the platform
  `MaxSocket` implementations (OkHttp on Android and JVM).
- `max/src/data/api/` — `MaxSession.kt` (MAX protocol mapping: sign-in, chats,
  history, live updates, profiles, search) and `MaxProvider.kt`.
  App entry points call `MaxIntegration.init()` next to the Telegram integration.
- `shared/src/data/api/ApiManager.kt` — account/session lifecycle for the app shell;
  it must not reference a concrete messenger provider.
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
  `libs.versions.toml` where available; pin exact versions. Don't hand-roll
  what a maintained KMP library already does — check klibs.io first (see
  *Libraries over reinvention*). Prefer stdlib/Compose when a library would add
  weight for one small helper.
- **File size limit: 1000 lines.** No source, test or build file may exceed 1000
  lines. Split it by concern instead — for example `FooScreen.kt` plus
  `FooScreenSections.kt` / `FooScreenDialogs.kt` in the same package, or
  `XSession.kt` plus `XSessionMapping.kt` / `XSessionChats.kt` holding internal
  extension functions on the session. Split along a cohesive boundary (a feature
  section, a protocol layer, a model mapper), not at an arbitrary line count, and
  keep `private` helpers `internal` when they cross the new file boundary.
  Files that already exceed the limit are grandfathered only until they are next
  touched; when editing one, split it in the same change.

## Libraries over reinvention

Before writing a layout helper, gesture handler, codec or other utility by
hand, check <https://klibs.io> — the Kotlin Multiplatform library catalog — for
a maintained package that already does it. Adopt one when it supports the
targets this project builds (Android + JVM), is actively maintained, has a
compatible license (Apache-2.0, MIT, BSD) and does not drag in a large native
payload.

When adopting a library:

- add the coordinate to the root `libs.versions.toml` with a pinned version
  alias, then reference it as `$libs.<alias>` from the module's `module.yaml`;
- keep the call site small, so replacing the library later stays a local
  change;
- say which hand-rolled code it replaced in the change summary.

Already in use: Compose Multiplatform, AndroidX Navigation 3, Material 3
adaptive, Room/SQLite, FileKit (file pickers), qrose (QR codes), Haze (blur),
compottie + Lottie (animations), OkHttp (transport), alpha-video/JavaCV
(video), KCEF (desktop web view), Human-Readable (time formatting),
kotlinx-datetime, kotlinx-serialization, and `sh.calvin.reorderable:reorderable`
for drag-to-reorder (it replaced the pointer-input implementation that used to
live in `ReorderDialog`).

Deliberately hand-rolled, with the reason:

- **`GifEncoder`** — MAX needs animated stickers as GIF; no maintained, tested
  KMP GIF *encoder* exists. The writer is covered by `GifEncoderTest`.
- **`EmojiCatalog`** — the picker mixes system emoji with provider custom
  emoji; the KMP emoji libraries target Android views or a different model.
- **`gunzip`** — `java.util.zip` exists on both targets, so a compression
  dependency would add weight without adding capability.
- **Material icon `ImageVector`s** — kept as generated files to avoid the
  `material-icons-extended` dependency and its APK size. New glyphs should be
  generated from the same Material Symbols source, not from a new icon library.
- **Location picker tile math** — a single OpenStreetMap tile for a static
  location card; a map SDK would be disproportionate.
- **Chat-list folder chip dragging** — the chips wrap inside a `FlowRow` and
  commit their order on release; Reorderable models lazy lists, not wrapping
  chip flows.
- **`PlatformBackHandler`** — Android already delegates to
  `androidx.activity.compose.BackHandler`; the desktop actual is intentionally
  empty because Escape arrives through `BackNavigation` from the window.

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

### Telegram test server

Scrollix can target Telegram's **test server** (separate DCs, accounts, chats).
A test build is fully isolated from a normal install.

1. Fill `telegram.apiId` / `telegram.apiHash` in `local.properties` from
   <https://my.telegram.org/apps>; that page also shows test DC addresses.
2. Set `telegram.testServer=true` (or `SCROLLIX_TEST_SERVER=1`) and rebuild.
   `BuildConfig.TEST_SERVER_MODE` turns on, `TelegramSession` passes TDLib
   `useTestDc = true`, storage moves to the `scrollix-test` profile, and
   MAX/VK are not registered (no public test server).
3. Sign in with a test account. The old `99966XYYYY` reserved numbers are
   disabled (TDLib abuse, see <https://github.com/tdlib/td/issues/3370>).
   Register a real number on the test DC via the official **iOS** app
   (Settings icon ×10 → Accounts → "Login to another account" → Test). The
   Play Store Android build cannot register test accounts, nor can TDLib itself.
4. Treat the test environment as public: anyone may claim the same number and
   Telegram wipes test data periodically. Never store real credentials or
   private messages there.

Test-server mode is a build flag, so a normal and a test build of the same
package cannot be distinguished at runtime. Prefer separate installs.

## Build and test

`kotlin` (macOS/Linux) and `kotlin.bat` (Windows) are self-bootstrapping
wrappers for Kotlin Toolchain `0.14.0`. The first invocation downloads the
toolchain and a JRE.

```sh
./kotlin build              # build all modules
./kotlin test               # run all tests
./kotlin test -m shared     # run tests for a single module
./kotlin run -m desktopApp  # run the desktop app
./kotlin run -m androidApp  # run the Android app
```

Prerequisites:

- Network access on the first build (toolchain download).
- `local.properties` with valid Telegram API credentials.
- For Android targets, an Android SDK discoverable by the toolchain
  (`ANDROID_HOME` / `ANDROID_SDK_ROOT`) with licenses accepted. Without accepted
  licenses the build stops at the SDK license check even if shared/JVM code
  compiled successfully.

### Tests

Tests use `kotlin.test` and mirror the package layout of the code they cover.
Run `./kotlin test -m shared` while iterating. Pure common tests (parsers,
formatters, history controllers) belong in `shared/test`; JVM or Android
`actual` behavior belongs in the matching platform test folder.

Golden-image tests render real Compose components offscreen (message bubble,
justified media grid, `ComposerInputRow`). Goldens are committed under
`shared/test@jvm/ui/golden/` and compared with a small channel tolerance for
font antialiasing. Regenerate only for an intended visual change:

```sh
SCROLLIX_UPDATE_GOLDENS=1 ./kotlin test -m shared
```

A mismatch writes the render under `build/golden-actual/`. Keep fixtures
deterministic (fixed width, timestamp, content); do not golden-test animated or
network-backed content.

Every `AccountSession` implementation must pass `contract/`'s
`AccountSessionContract`. Each provider module has one test subclass:
`telegram/test@jvm/TelegramSessionContractTest.kt`,
`max/test/MaxSessionContractTest.kt`, `vk/test/VkSessionContractTest.kt`.
The suite constructs a real session against in-memory storage and checks
provider metadata, session identity, the initial empty state and the close
lifecycle — no credentials or network. Telegram's contract test lives in
`test@jvm` because the Android JVM variant links `tdl-coroutines-android`, which
needs the `tdjsonjava` native library that desktop test runs do not load. When
adding a provider, subclass the contract; do not duplicate the suite.

### Interactive UI checks

On a Wayland host, clicking through the desktop app is unreliable: the Compose
window is an Xwayland client, XTest-injected pointer events are not delivered,
and root screenshots are black (`Robot` fails with "Could not load native
libraries for ScreencastHelper"; ImageMagick `import` and `ffmpeg -f x11grab`
capture an empty root). The app window itself can still be captured by X11 id;
throwaway helpers live in `build/uiauto/` (gitignored with `build/`).

Verify taps, scrolling and navigation on the Android emulator instead:

```sh
~/AndroidSdk/emulator/emulator -avd Pixel_9_API_37.2 \
  -no-window -gpu swiftshader_indirect -no-snapshot &
adb wait-for-device
adb install -r build/tasks/_androidApp_buildAndroidDebug/gradle-project/build/_androidApp/outputs/apk/debug/gradle-project-debug.apk
adb shell am start -n com.mrboomdev.scrollix/.MainActivity
adb exec-out screencap -p > /tmp/screen.png
adb shell input tap <x> <y>
adb shell input swipe <x1> <y1> <x2> <y2> <duration_ms>
```

`./kotlin build -m androidApp` produces that debug APK.

Emulator gotchas (example session, Nov 2026 — coordinates/AVDs may drift):

- `Pixel_9_API_37.2` boots headless at 1080x2424 with the `google_apis` x86_64
  image. First launch shows the `POST_NOTIFICATIONS` dialog; dismiss with
  `adb shell input tap 540 1395` ("Don't allow").
- `adb exec-out screencap -p` captures correctly even when host screenshots are
  black, and `adb shell input tap/swipe` reaches Compose.
- Settings is behind the overflow menu on mobile (tap 996,137), not only the
  wide-layout rail.
- `adb shell dumpsys clipboard` confirms "Copy debug info" put a text/plain clip
  owned by `com.mrboomdev.scrollix`.
- A `telegram.testServer=true` build shows `Edition: Test server` and
  `Storage profile: scrollix-test` in Settings → Diagnostics.

## Feature behavior notes

Keep these invariants when changing related code.

Index: **Media & cache** · **Sessions, auth & data** · **Composer, outbox & sending** ·
**Profiles, navigation & search** · **Notifications & calls** · **Diagnostics & privacy**

### Media & cache

- **Media cache identity.** Cached media is keyed by TDLib's durable remote
  unique id, scoped by account. `CacheStore` hex-encodes ids because unique ids
  are base64 and can contain `/`, `+`, `=`; a lossy filename mapping made
  distinct files collide. `MessageMedia` carries `previewUniqueId`,
  `fileUniqueId`, `stickerUniqueId`. UI code loads media through
  `AccountSession.loadMediaBytes(fileId, uniqueId)`: cached bytes preferred, and
  a runtime file id is used only when `loadFileIfUnique` confirms it still
  resolves to the same durable id. Never render media from a bare file id that
  came from a cached message or dialog.
- **Storage budget.** `AppSettings.mediaCacheLimitBytes` caps cached media
  (stickers, custom emoji, avatars); `0` = unlimited, default 512 MB.
  `CacheStore.enforceMediaLimit` deletes oldest files first using
  `PlatformStorage.listEntries` (size, mtime), running at startup and after
  roughly 4 MB / 60 s of new media. Dialogs and cached messages are never
  evicted by this budget; test storage may keep the default `listEntries` (zero
  metadata) when it does not need it.
- **Keep media.** Settings → Storage → Keep media owns this list; the chat
  profile has no switch. `KeepMediaSection` stores
  `keepMediaKey(accountId, providerDialogId)` in
  `AppSettings.keepMediaDialogIds`, shows kept chats per account (titles come
  from `CacheStore.loadDialogs`, an uncached chat falls back to its raw key)
  and offers every cached chat in a picker. `CacheStore` protects only media
  referenced by that chat's cached sent messages (`mediaUniqueIds`: albums,
  replies, poll options, rich blocks). Protected files still count toward the
  budget but are never evicted, so a cache of only protected media can stay
  above the limit. Protection is refreshed at startup, after toggling, and when
  a kept chat's messages are cached; manually clearing the media category still
  deletes it.
- **Service media.** `MessageChatChangePhoto` and `MessageChatSetBackground` map
  to photo media, so chat bubbles and dialog previews show the new avatar or
  wallpaper instead of only a text notice.
- **Location sharing.** `AccountSession.sendLocation` sends a static point or,
  when `livePeriodSeconds > 0`, `InputMessageLiveLocation` (never a fake live
  period on `InputMessageLocation`). Active shares are stopped via
  `stopLiveLocation` → `editMessageLiveLocation(location = null)`. The picker
  uses one OpenStreetMap tile through `fetchUrlBytes` with a client User-Agent;
  do not bulk-download tiles or pre-decode media for the location card.
- **Video playback.** `VideoPlayer` in `ui/components` owns the controls
  (play/pause, seek, elapsed/total, mute, buffering, error) and delegates
  decoding to `VideoPlayback`/`VideoRenderSurface`. Android uses `MediaPlayer`
  with a `TextureView` and the system dialer intent; desktop streams frames from
  `FFmpegFrameGrabber` and plays audio through `createVoicePlayer()`. Do not
  pre-decode whole video files into memory.

### Sessions, auth & data

- **Session errors.** Provider code should return `Result.failure` with a
  `SessionException` (code, retryability, original message) instead of raw
  exceptions. All providers map their failures: Telegram wraps every
  `TdlResult.Failure`; MAX's `MaxApiException`/`MaxConnectionClosedException` and
  VK's `VkApiException` extend `SessionException`; numeric/dotted mapping lives
  in `MaxErrors.kt`/`VkErrors.kt`; remaining MAX/VK paths wrap raw errors with
  `asSessionException()`. Keep the provider's original text in `message` so the
  UI and diagnostics stay actionable.
- **Sessions and data use.** Settings → Sessions lists server-side devices
  through `AccountSession.loadActiveSessions` and signs them out with
  `terminateSession` / `terminateOtherSessions`; these default to "unsupported"
  in the provider contract and Telegram maps them to TDLib active sessions. The
  current device is never offered for termination in the UI (it would sign the
  user out).
- **Data saver is local.** Settings → Data and network never calls
  `loadDataUsage`/`applyDataUsagePreset`; the provider methods stay in the
  contract but the app does not depend on them. Traffic comes from
  `DataUsageStore` (`data-usage.json`): `CacheStore.saveMedia` records received
  bytes and a successful text send in `OutboxManager` records sent bytes, so the
  numbers cover only traffic Scrollix itself moves and are labelled as
  approximate. Resetting clears that account's counters. The profile itself is
  `AppSettings.dataSaving` (`DataSavingSettings`): Presets Low/Medium/High set
  the auto-play switches for GIFs/animated stickers and round video notes, and
  any manual change turns it into a custom profile (`matchingPreset == null`).
  `ChatAnimation` and `ChatVideo` read the switches and leave the still preview
  in place instead of downloading when playback is off.
- **Accounts.** The "Active" account is not a chat-list filter. It is the
  default account used by settings pickers (packs/reactions) and the QR
  scanner; the chat list intentionally aggregates every account.
- **Accounts and calls.** Adding an account opens a provider chooser; Telegram
  is the only registered provider today. Telegram accounts and profiles show
  the brand mark. The profile call button is Android-only and uses `openDialer`
  (ACTION_DIAL), never an immediate call intent.
- **Storage migrations.** Every persisted JSON shape carries a version: bump
  `AppSettings.settingsVersion` or `AccountStoreState.version` for an
  incompatible change, add a pure `migrate…` step, and cover both the object and
  a legacy JSON file in tests. Older files decode through serialization
  defaults; a version newer than this build is never rewritten. The Room cache
  DB exports its schema to `shared/schemas/` (`exportSchema = true`,
  `room.schemaLocation` in `shared/module.yaml`); commit those files.
  `CacheDatabaseMigrationTest` creates the committed schema through
  `MigrationTestHelper` and checks version + identity hash. Backup restore
  goes through `previewSettingsBackup` first: it migrates the file, refuses a
  `settingsVersion` newer than this build (importing would drop its unknown
  fields and rewrite it in the old shape) and returns a human-readable change
  list from `settingsChanges`. The UI only calls `applyImport` after the user
  confirms the preview; `importJson` stays as a boolean shortcut for tests. Bump
  `CacheDatabaseVersion` only with an explicit migration and its vN→vN+1 test
  (destructive fallback is a deliberate decision, not a default), and treat
  cached payload compatibility as part of the schema because dialogs/messages
  are stored as serialized models. `migrateCachedMessage` upgrades cached rows
  on read (legacy service text, avatar bitmaps).
- **State-file integrity.** An unreadable `settings.json` or `outbox.json` is
  no longer replaced in memory and then overwritten by the next save:
  `StorageIntegrity.quarantine` copies the bytes to `<name>.corrupt`, deletes
  the original so the next launch starts clean, and logs a warning. Diagnostics
  → Storage health lists the copies with their size and deletes them; the app
  has already fallen back to defaults, so restoring is a manual decision. At
  startup `ApiManager.init` also calls `OutboxManager.pruneUnknownAccounts`
  with the known account ids, which drops queued work of removed accounts (and
  its retry jobs). Pruning is skipped when the account list is empty, so an
  unreadable account file cannot wipe the queue.
- **Drafts.** Composer text is stored on Telegram's servers via
  `AccountSession.saveDraft` (TDLib `setChatDraftMessage`) and restored with
  `loadDraft` when a chat opens. Draft text and custom-emoji spans travel
  together. Debounced saves must not clear an unreadable or not-yet-loaded
  draft.

### Composer, outbox & sending

- **Outbox.** Text sends, file sends, edits and retryable deletions go through
  `OutboxManager`, not the composer calling the session directly. Entries
  persist in `outbox.json` (`OutboxEntryKind` selects the operation), are
  retried with backoff for retryable `SessionException` codes (offline, timeout,
  flood wait), and text/file sends render from the queue with negative local
  message ids so they survive chat switches and restarts. A permanent error
  becomes a `Failed` row: retry from the bubble menu or by tapping "Not sent",
  or cancel it. Deleting a local row cancels the queued send; deleting delivered
  messages queues only after a retryable failure. File retries reuse the
  original local paths, so a temp file removed by the OS fails permanently
  instead of silently sending the wrong file. Removing an account clears its
  queue. Delivery is at-least-once: a timeout after the server stored the
  message can duplicate it — do not add another retry loop on top. Each failed
  attempt is appended to `OutboxEntry.attemptHistory` (capped by
  `appendOutboxAttempt`) and shown as a retry timeline in Diagnostics. While a
  signed-in account is disconnected, `ConnectionBanner` above the chat list
  names it, shows the queued count from `OutboxManager.entries` and runs
  `OutboxManager.retryAll()` on "Retry now"; `ApiManager.connectionStates`
  combines each session's `isConnected` flow and omits accounts without a
  session, so signing in is never reported as offline.
- **Attachment review.** Picking one or more files opens a blocking,
  non-dismissible modal that dims the whole chat. It lists the attachments and
  an optional caption, and the only actions are Send and Discard. Do not add
  dismiss-on-back, dismiss-on-outside, or usable composer controls underneath
  it (review must be explicit). Rows are numbered by their album position and
  have ↑/↓ buttons (backed by the pure `List.moved`) plus remove, so the send
  order can be fixed before uploading; the arrows are disabled at the edges and
  while a send is in flight.
- **Composer layout.** The message list is the background of the chat area and
  scrolls behind the floating composer. The list's bottom content padding is
  the measured composer height, so the newest message rests above the composer
  at the end of history instead of being hidden by it.
- **Message animations.** Newest messages slide in from the bottom, and
  `Modifier.animateItem()` handles deletion/placement in the history list.
- **Batch copy.** The selection bar's Copy action uses `selectedMessageText`:
  service and blank messages are skipped, an album row counts once, messages
  are joined oldest first (independent of the list's display order) and a blank
  line separates them. Copying clears the selection so the bar closes.
- **Forwarding picker.** The `Forward to` dialog only offers chats that accept
  messages in the current chat's account (not the current chat itself).
  `filterForwardTargets` matches the title with every whitespace token, then
  the folder chips narrow the result; the query and folder reset each time the
  dialog opens. Run the query first and the folder tabs after it, so a search
  is still narrowed by the selected tab.
- **Message emoji packs.** The message action menu lists packs that actually
  occur in that message: sticker media `stickerSetId` plus the set ids returned
  by `loadCustomEmojiSetIds` for custom emoji spans. Selecting a pack opens
  `StickerSetDialog` through `onOpenStickerSet`.
- **Reactions.** `pinnedCustomReactions` and `doubleTapReaction` in
  `AppSettings` are `ReactionPreference` values that may carry a custom emoji
  id. Double-tap reactions are only wired on compact/mobile layouts; the
  message bubble shows selection checkboxes while selection mode is active.

### Profiles, navigation & search

- **Fullscreen media viewer.** Only the explicit close button or a tap on the
  backdrop closes it. Taps on the image or video content itself are consumed
  and must not dismiss the viewer. Photos zoom with a pinch, pan while zoomed
  and toggle fitted/2.5× on double tap around the tapped point;
  `clampZoomOffset` keeps the pan inside the container and `transformable`'s
  `canPan = scale > 1` leaves horizontal swipes to the gallery pager. The pager
  sets `userScrollEnabled = false` while the visible page is zoomed and each
  page resets its zoom when it becomes inactive, so reopening starts fitted.
  The message caption travels through `MediaViewer` (`MediaViewerRequest` /
  `MediaGalleryRequest.caption`) and is drawn on a scrim, above the thumbnail
  strip in the gallery and at the bottom of a single viewer; it is clamped to
  four lines with ellipsis, since the full text is still in the chat behind.
- **Own profile.** The accounts screen opens `Route.OwnProfile`, which renders
  `AccountSession.loadOwnProfile()` instead of a dialog profile. It returns the
  same `UserProfileDetails` shape as other profiles, including the personal
  channel card and `upgradedGiftColors`, so the header gradient works there too.
- **Profile photos.** Clicking a user avatar in the profile opens
  `ProfileAvatarGalleryDialog`, which lists `getUserProfilePhotos` and can save
  a photo with `saveFileToDevice`.
- **Profile colors.** `UserProfileDetails.accentColorsLight/Dark` come from
  `User.upgradedGiftColors` when the user has a collectible gift. The profile
  header and avatar ring use them as a translucent gradient; leave the default
  surface styling when they are empty.
- **Profile transitions.** `App` wraps `NavDisplay` in `SharedTransitionLayout`
  and provides `LocalSharedTransitionScope`; avatar/name wrappers use
  `SharedProfileElement` with keys `profile:<account>:<dialog>` (or
  `own-profile:<account>`) so the chat header and accounts row fly into the
  profile header.
- **Mention avatars.** `TelegramSession.loadMentionAvatars` resolves usernames
  once per session; `CustomEmojiMessageText` renders them as inline content
  before the handle. The mention itself stays the only tap target, and the
  avatar must not swallow bubble gestures.
- **Shared media.** Profile screens list photos, videos, GIFs, voice notes,
  links and files with `loadSharedMedia`, paged by `fromMessageId`; every row
  jumps to its message through `Route.Dialog(messageId = …)`.
- **Link previews.** `MessageText.linkPreview` is mapped onto
  `ChatMessage.linkPreview` (`MessageLinkPreview`). `MessageBubble` renders the
  preview card above or below the text per TDLib `showAboveText`. Opening a
  preview URL runs through `DeepLinkHandler`, so t.me previews stay in the app;
  other URLs go to the system browser.
- **Hashtags.** Message text, rich text and custom-emoji text share
  `appendTextWithLinks`, which makes `#hashtag` clickable alongside mentions,
  URLs and phone numbers. Tapping a hashtag opens `Route.Search(query)` with the
  Posts/Messages tab preselected, so `searchGlobalMessages` returns matches
  across every account instead of only filtering the current chat.
- **Telegram deep links.** `DeepLinkHandler` bridges platform intents to
  Compose. Android declares VIEW filters for `t.me`, `telegram.me` and the `tg`
  scheme in `AndroidManifest.xml`; `MainActivity` publishes both the launch
  intent and `onNewIntent`. `App` resolves the pending link with
  `AccountSession.resolveTelegramLink` (public usernames, invite links, joined
  chats) and navigates to `Route.Dialog`, including the message id for
  `t.me/<name>/<id>` links. Keep link handling in this path instead of calling
  `LocalUriHandler` for Telegram URLs.
- **Selected dialog.** The wide-layout chat pane selection is
  `rememberSaveable` in `App`, so opening Settings, Search or recreating the
  activity keeps the open chat. The shared `ReorderDialog` changes order with
  long-press dragging; pickers apply pack order through `orderedStickerPacks`,
  unlisted packs keep server order.
- **Reduce motion.** Settings → Accessibility sets `AppSettings.reduceMotion`.
  `DialogScreen` then skips the slide-in of a just-sent message (the row is
  rendered directly) and `SharedProfileElement` skips `sharedElement` while
  keeping its fade, so avatars and names no longer fly between screens. Any
  new decorative animation should read the same flag rather than adding a
  second switch; functional feedback (banners, loading indicators) stays.
- **Settings sections and search.** Sections are `SettingsCategory`; the
  search field matches `SettingsEntries`, a hand-maintained index of every row
  (including rows of screens that were never opened), and falls back to a
  single section result when only its title/keywords match. Add a row to the
  index when adding a setting. The open section is `rememberSaveable` in both
  the one-pane and two-pane layouts, so it survives navigation and recreation.
  There is no per-section reset. Accounts and the outgoing queue are not
  settings sections: `Route.Accounts` owns the account list, per-account
  connection state and the queue with its retry/discard actions.
- **Folder and pack order.** `AppSettings.folderOrder` stores chip keys (`all`,
  `folder:<account>:<folder>`, `tab:<index>`), and
  `stickerPackOrder`/`emojiPackOrder` store pack ids.
- **Pinned chat order.** Telegram has no reorder call, so
  `reorderPinnedDialogs` unpins the supplied chats and re-pins them in reverse
  order to make the server store the requested order. Pass every pinned chat in
  the target chat list, not only the moved one.

### Notifications & calls

- **Notifications.** Scope-wide mutes (private chats, groups, channels) and
  per-chat mutes are server-side TDLib settings: `loadNotificationSettings`,
  `setScopeMuted`, `setDialogMuted`, `isDialogMuted`. The settings screen
  exposes them per account; profiles expose the per-chat mute toggle. Do not
  move these to local-only storage. New unread messages raise an OS
  notification through `showDesktopNotification`, which `App` calls only for
  unmuted chats and only when the unread counter grows. Android posts them on
  the `messages` channel (`createNotificationChannels`), requests
  `POST_NOTIFICATIONS`, groups chats per account behind a silent summary, and
  suppresses them while an activity is in front;
  tapping one opens the chat through `NotificationExtras` and `ChatOpenHandler`.
  Notifications only arrive while the process is alive (no push service).

### Diagnostics & privacy

- **Persistent diagnostics.** `ApiManager.init` calls
  `DiagnosticsFileLog.install(storage)`, which forwards every `AppLog` entry to
  `diagnostics/scrollix.log` through a channel, so logging never blocks on
  disk. The file rotates at ~256k chars to `scrollix.log.1`; Diagnostics can
  copy and clear it. It stays on the device: never upload it or log secrets,
  message contents or tokens. Clearing the in-memory log from Diagnostics also
  clears the file. Protocol dumps are an explicit opt-in toggle in Diagnostics
  (`AppSettings.protocolDumpsEnabled`, synced by `ApiManager.init`); MAX frames,
  VK calls and Telegram updates then land in the same log through
  `AppLog.protocol`, which redacts credential-looking values and truncates long
  payloads. Message text may remain, so keep the toggle off by default and never
  enable it on someone else's device.
- **In-memory diagnostics screen.** `AppLog` in the `provider` module keeps a
  bounded in-memory log. Session start failures, provider auth errors, storage
  failures and unhandled deep links record into it; Settings → Diagnostics opens
  `DiagnosticsScreen`, which copies the report as text and toggles redacted
  protocol dumps. Its Network card lists per-account connection state from
  `ApiManager.connectionStates`, the outbox waiting/sending/failed counts with
  the last queue error, the retry timeline and a retry-all action, so a stuck
  send can be diagnosed without opening the chat. The screen is a normal `Route.Diagnostics` entry,
  so it appears in the wide layout and can be opened from the settings category
  list.
- **Crash reports.** Both entry points call `CrashReports.install()`, which
  installs a process-wide uncaught-exception handler and always delegates to the
  previous handler. A report is written to `diagnostics/last-crash.txt` only
  when `AppSettings.crashReportsEnabled` is on (default off); it contains
  environment facts, the full stack and the last 80 log entries. Diagnostics
  shows it with Copy/Delete. Handled failures logged with a throwable reach the
  same store through `AppLog.attachNonFatalHandler` and are written separately
  to `diagnostics/last-nonfatal.txt`, so they do not overwrite a real crash.
  Never upload either report automatically or collect it without that toggle;
  remote crash upload is deliberately not implemented.

## Adding a provider

1. Create a module folder (e.g. `signal/`) with `module.yaml` depending on
   `provider` and `contract`, plus platform source sets for transports.
2. Implement `AccountSession` in `signal/src/data/api/` and map every failure to
   `SessionException` (see *Session errors*).
3. Register the provider with `ProviderRegistry` from a `SignalIntegration`
   object; call `SignalIntegration.init()` in the Android and desktop entry
   points next to the existing integrations.
4. Add `signal/test/SignalSessionContractTest.kt` subclassing
   `AccountSessionContract`. Do not duplicate the suite per provider.
5. If the provider needs generated config, wire the `build-config` plugin the
   same way the `telegram` module does.

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
```

---

## What changed

**Cut / condensed**
- Test-server section: 30 lines → 15 (kept every actionable fact, dropped prose).
- Interactive UI checks: merged the Wayland note and the emulator bullets, reframed the machine-specific section as an "example session" so it reads as illustrative rather than canonical.
- Configuration + Build prerequisites: folded the duplicated "local.properties / SDK licenses" warnings into one place.
- Merged the two diagnostics bullets and separated "persistent file log" from "in-memory screen".

**Added**
- **Quick start** block at the top — an agent can build/run in ~10 lines without scrolling.
- **Index** for the feature notes (six categories: Media & cache, Sessions & data, Composer/outbox, Profiles & navigation, Notifications & calls, Diagnostics & privacy). Feature notes are now grouped under those headings.
- **"Adding a provider"** checklist — the original had all the pieces scattered across `contract/`, `ProviderRegistry`, and integration `init()` calls but no single path.
- One-line rationale added where a rule was stated without *why* (current device never offered for termination; attachment review must be explicit).
- Dependency guidance: "prefer stdlib/Compose over a new library; pin exact versions."

**Preserved verbatim (or nearly)**
- Every invariant in the original feature notes — hex encoding, 512 MB / 4 MB / 60 s, negative local message ids, `profile:<account>:<dialog>` keys, `folder:<account>:<folder>`, `99966XYYYY`, `useTestDc`, `scrollix-test`, 256k rotation, 80 log entries, etc.
- All command blocks (`./kotlin …`, `SCROLLIX_UPDATE_GOLDENS`, `adb …`, `./scripts/release.sh …`).
- Full repository layout table and main-packages list.