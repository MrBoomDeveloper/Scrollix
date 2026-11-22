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
| `shared/` | Kotlin Multiplatform library with all UI and app-shell logic (Android + JVM) |
| `provider/` | Messenger contracts and plain data models (`AccountSession`, `MessengerProvider`, `ChatMessage`, …) |
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

## Feature behavior notes

Keep these invariants when changing the related code:

- **Media cache identity.** Cached media is keyed by TDLib's durable remote
  unique id, scoped by account. `CacheStore` hex-encodes ids because unique ids
  are base64 and can contain `/`, `+` and `=`; a lossy filename mapping made
  distinct files collide. `MessageMedia` carries `previewUniqueId`,
  `fileUniqueId` and `stickerUniqueId`. UI code loads media through
  `AccountSession.loadMediaBytes(fileId, uniqueId)`: cached bytes are preferred
  and a runtime file id is used only when `loadFileIfUnique` confirms it still
  resolves to the same durable id. Never render media from a bare file id that
  came from a cached message or dialog.
- **Drafts.** Composer text is stored on Telegram's servers with
  `AccountSession.saveDraft` (TDLib `setChatDraftMessage`), and restored with
  `loadDraft` when a chat opens. Draft text and custom-emoji spans travel
  together. Debounced saves must not clear an unreadable or not-yet-loaded
  draft.
- **Attachment review.** Picking one or more files opens a blocking,
  non-dismissible modal that dims the whole chat. It lists the attachments and
  an optional caption, and the only actions are Send and Discard. Do not add
  dismiss-on-back, dismiss-on-outside, or usable composer controls underneath
  it.
- **Composer layout.** The message list is the background of the chat area and
  scrolls behind the floating composer. The list's bottom content padding is
  the measured composer height, so the newest message rests above the composer
  at the end of history instead of being hidden by it.
- **Fullscreen media viewer.** Only the explicit close button or a tap on the
  backdrop closes it. Taps on the image or video content itself are consumed
  and must not dismiss the viewer.
- **Notifications.** Scope-wide mutes (private chats, groups, channels) and
  per-chat mutes are server-side TDLib settings: `loadNotificationSettings`,
  `setScopeMuted`, `setDialogMuted` and `isDialogMuted`. The settings screen
  exposes them per account, and profiles expose the per-chat mute toggle. Do
  not move these to local-only storage.
- **Reactions.** `pinnedCustomReactions` and `doubleTapReaction` in
  `AppSettings` are `ReactionPreference` values that may carry a custom emoji
  id. Double-tap reactions are only wired on compact/mobile layouts; the
  message bubble shows selection checkboxes while selection mode is active.
- **Own profile.** The accounts screen opens `Route.OwnProfile`, which renders
  `AccountSession.loadOwnProfile()` instead of a dialog profile. It returns the
  same `UserProfileDetails` shape as other profiles, including the personal
  channel card and `upgradedGiftColors`, so the header gradient works there too.
- **Video playback.** `VideoPlayer` in `ui/components` owns the controls
  (play/pause, seek, elapsed/total time, mute, buffering and error state) and
  delegates decoding to `VideoPlayback`/`VideoRenderSurface`. Android uses
  `MediaPlayer` with a `TextureView` and the system dialer intent; desktop
  streams frames from `FFmpegFrameGrabber` and plays audio through
  `createVoicePlayer()`. Do not pre-decode whole video files into memory.
- **Accounts.** The "Active" account is not a chat-list filter. It is the
  default account used by settings pickers (packs/reactions) and the QR
  scanner; the chat list intentionally aggregates every account.
- **Accounts and calls.** Adding an account opens a provider chooser; Telegram
  is the only registered provider today. Telegram accounts and Telegram
  profiles show the brand mark. The profile call button is Android-only and
  uses `openDialer` (ACTION_DIAL), never an immediate call intent.
- **Service media.** `MessageChatChangePhoto` and `MessageChatSetBackground`
  map to photo media, so chat bubbles and dialog last-message previews show the
  new avatar or wallpaper instead of only a text notice.
- **Location sharing.** `AccountSession.sendLocation` sends a static point or,
  when `livePeriodSeconds > 0`, `InputMessageLiveLocation` (never a fake live
  period on `InputMessageLocation`). Active shares are stopped with
  `stopLiveLocation` → `editMessageLiveLocation(location = null)`. The picker
  uses one OpenStreetMap tile through `fetchUrlBytes` with a client User-Agent;
  do not bulk-download tiles or pre-decode media for the location card.
- **Mention avatars.** `TelegramSession.loadMentionAvatars` resolves usernames
  once per session and `CustomEmojiMessageText` renders them as inline content
  before the handle. The mention itself stays the only tap target, and the
  avatar must not swallow bubble gestures.
- **Profile photos.** Clicking a user avatar in the profile opens
  `ProfileAvatarGalleryDialog`, which lists `getUserProfilePhotos` and can save
  a photo with `saveFileToDevice`.
- **Shared media.** Profile screens list photos, videos, GIFs, voice notes,
  links and files with `loadSharedMedia`, paged by `fromMessageId`; every row
  jumps to its message through `Route.Dialog(messageId = …)`.
- **Message animations.** Newest messages slide in from the bottom, and
  `Modifier.animateItem()` handles deletion/placement in the history list.
- **Profile colors.** `UserProfileDetails.accentColorsLight/Dark` come from
  `User.upgradedGiftColors` when the user has a collectible gift. The profile
  header and avatar ring use them as a translucent gradient; leave the default
  surface styling when they are empty.
- **Profile transitions.** `App` wraps `NavDisplay` in `SharedTransitionLayout`
  and provides `LocalSharedTransitionScope`; avatar/name wrappers use
  `SharedProfileElement` with keys of the form `profile:<account>:<dialog>`
  (or `own-profile:<account>`) so the chat header and accounts row fly into
  the profile header.
- **Folder and pack order.** `AppSettings.folderOrder` stores chip keys
  (`all`, `folder:<account>:<folder>`, `tab:<index>`), and
  `stickerPackOrder`/`emojiPackOrder` store pack ids.
- **Selected dialog.** The wide-layout chat pane selection is `rememberSaveable`
  in `App`, so opening Settings, Search or recreating the activity keeps the
  open chat. The shared
  `ReorderDialog` changes the order with long-press dragging; the pickers apply
  pack order through `orderedStickerPacks`, unlisted packs keep server order.
- **Pinned chat order.** Telegram has no reorder call, so
  `reorderPinnedDialogs` unpins the supplied chats and re-pins them in reverse
  order to make the server store the requested order. Pass every pinned chat
  in the target chat list, not only the moved one.
- **Message emoji packs.** The message action menu lists packs that actually
  occur in that message: sticker media `stickerSetId` plus the set ids returned
  by `loadCustomEmojiSetIds` for custom emoji spans. Selecting a pack opens
  `StickerSetDialog` through `onOpenStickerSet`.
- **Link previews.** `MessageText.linkPreview` is mapped onto
  `ChatMessage.linkPreview` (`MessageLinkPreview`). `MessageBubble` renders the
  preview card above or below the text according to TDLib
  `showAboveText`. Opening a preview URL runs through `DeepLinkHandler`, so
  t.me previews stay in the app; other URLs go to the system browser.
- **Hashtags.** Message text, rich text and custom-emoji text share
  `appendTextWithLinks`, which makes `#hashtag` clickable in addition to
  mentions, URLs and phone numbers. Tapping a hashtag opens
  `Route.Search(query)` with the Posts/Messages tab preselected, so
  `searchGlobalMessages` returns matches across every account instead of only
  filtering the current chat.
- **Telegram deep links.** `DeepLinkHandler` bridges platform intents to
  Compose. Android declares VIEW filters for `t.me`, `telegram.me` and the
  `tg` scheme in `AndroidManifest.xml`, and `MainActivity` publishes both the
  launch intent and `onNewIntent`. `App` resolves the pending link with
  `AccountSession.resolveTelegramLink` (public usernames, invite links and
  joined chats) and navigates to `Route.Dialog`, including the message id for
  `t.me/<name>/<id>` links. Keep link handling in this path instead of calling
  `LocalUriHandler` for Telegram URLs.

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
