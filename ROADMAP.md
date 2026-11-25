# Scrollix roadmap

A living plan for turning Scrollix from a solid multi-provider chat client into
a messenger that can replace the official apps for everyday use on Android and
desktop. It is ordered by user value per unit of work, not by how easy it is.

**Legend:** `[x]` done · `[~]` partially done · `[ ]` not started.

**Non-goals for this roadmap:** voice and video calls, call UI, call history,
call notifications and call-related system integrations. Everything else needed
for a complete messenger is in scope.

---

## 0. Where the app stands today

Implemented and verified in the current codebase:

- [x] Kotlin Multiplatform app shell (Compose Multiplatform, Android + desktop)
- [x] Multi-account core with a provider abstraction (`provider/`), Telegram,
      MAX and VK providers behind one `AccountSession` contract
- [x] Dialog list with server folders, drag-reordering, pinning, unread
      counters, per-account badges, optional cross-account merging
- [x] Message history with windowed pagination, jumps, an explicit
      undiscovered gap that is filled lazily, and a non-animating jump to end
- [x] Composer: text, custom emoji, replies, editing, attachments, albums with
      a blocking review sheet, media-recording (voice + circle video notes with
      live preview, cancel/send), polls, locations, drafts
- [x] Message rendering: media albums, stickers (static/TGS/WebM), GIFs,
      reactions (including channel-restricted sets), reply quotes, link
      previews, buttons, service messages, spoilers, translation-ready layout
- [x] Profiles: own profile, user/group/channel profiles, avatars gallery,
      personal channels, groups in common, shared media tabs, verified badge,
      notification mutes, member lists, accent colors
- [x] Search: chats, public chats, posts, in-chat messages, hashtags,
      cross-account global message search
- [x] Notifications: server-side scope mutes with per-chat exceptions,
      per-chat mutes, OS notifications, deep links into chats
- [x] Forum topics for groups (read, browse, send) and channel admin posting
- [x] Settings: chat list, notifications, backup/restore, storage, accounts
- [x] Navigation 3 list/detail scenes for wide layouts
- [x] Test-server mode (isolated profile, Telegram test DC, other providers
      disabled)
- [x] Offline outbox for text/files/edits/retryable deletions with backoff,
      restart recovery and manual retry
- [x] Local diagnostics, opt-in protocol dumps, local crash and non-fatal reports
- [x] Provider contract tests and a golden-image harness for core UI surfaces

The rest of this document is what still separates that from a messenger people
can use every day.

---

## 1. Foundations (done — keep them healthy)

- [x] Test-server build mode with a fully separate storage profile
- [x] Structured logging + diagnostics screen with a "copy debug info" action
      (in-memory log plus a rotating on-device file, copy/clear controls, and an
      opt-in protocol-dump toggle with credential redaction across Telegram,
      MAX and VK)
- [x] Crash capture with opt-in consent: local reports for uncaught crashes and
      handled errors, with environment facts, stack trace and recent log lines,
      copyable/deletable from Diagnostics. Nothing is uploaded automatically;
      remote upload is intentionally absent under the local-only policy
- [x] Offline-first outbox: text and file sends, edits and retryable deletions
      persist in `outbox.json`, retry with backoff across restarts, render
      Sending/Failed rows for sends, and expose a manual retry action
- [x] Unified error model (`SessionException` with codes, retryability and
      human text). Telegram, MAX and VK transport/result failures map to the
      same type while keeping the raw provider exception as the cause
- [x] Database migration policy and automated migration tests: versioned
      settings/account stores with pure migrations and legacy-file tests,
      cached-payload upgrades, exported Room schemas and a
      `MigrationTestHelper` creation/identity test. A future schema bump must
      add its vN→vN+1 migration test
- [x] Storage budget manager: media cache limit with oldest-first
      auto-eviction, startup enforcement, a size breakdown with manual cleanup,
      and per-chat "keep media" protection for cached message media
- [x] Provider contract tests: one shared `AccountSessionContract` suite that
      Telegram (JVM), MAX and VK sessions pass offline, including provider
      metadata, identity, initial state and close lifecycle
- [x] Golden-image tests: an offscreen Compose harness with committed PNGs and
      an update switch covers an outgoing text bubble, the justified media grid
      and the extracted `ComposerInputRow`

### Foundation follow-ups

- [ ] Extend provider contract tests from identity/lifecycle to pure protocol
      mapping: DTO → model fixtures for messages, media, folders, reactions,
      profiles, errors and pagination
- [ ] Add a fake provider implementation used by shared UI tests and previews
- [ ] Record deterministic transport fixtures for MAX frames and VK responses,
      then replay them in tests instead of hand-built JSON only
- [ ] Add an integration smoke suite against a real Telegram test account,
      gated behind an environment variable and never run by default
- [ ] Add a central error-report envelope so UI screens can show one consistent
      retry/report surface instead of ad-hoc `error = ...` strings

---

## 2. Core messaging parity (Telegram first, then MAX/VK)

- [~] Text, custom emoji, replies, forwarding, editing, deletion, pinning
- [ ] Rich text composer: bold/italic/mono/underline/strike/spoiler, quote
      blocks, lists, code blocks, link editing, entity-aware paste
- [ ] Markdown/HTML paste conversion and "send without formatting"
- [ ] Entity-aware editing: preserve spans while typing, deleting and pasting;
      undo/redo for composer edits
- [ ] Scheduled messages: create, list, reschedule, send now, delete, time-zone
      display, "schedule when online" for offline use
- [ ] Message drafts per topic/thread with conflict resolution between devices
- [x] Message links (`t.me/...`) that open the exact message, including
      private and topic links
- [ ] Replies 2.0 UI: quote selection, jump to quote, reply previews with
      media, reply in another chat
- [~] Forwarding: picker with a title search, native/synthetic folder tabs,
      an empty state, "hide sender" (re-sent as a copy, no forward header) and
      "remove caption". Both options are gated on
      `AccountSession.supportsForwardOptions`; Telegram honors them, MAX and VK
      never show them. Forwarding to topics is still missing
- [~] Message selection: select-all of the loaded window, batch
      forward/delete/copy. Range select by drag and per-account actions are
      still missing
- [~] In-chat search with sender and media-type filters (all / from me /
      photos / videos / files / links) over the matched page; results replace
      the history while a query is active. Result paging, date filters and
      highlighted matches are still missing
- [ ] Global message search across accounts with filters and saved queries
- [~] Read receipts, typing indicators and online status are shown; "mark as
      unread" works on Telegram (TDLib `toggleChatIsMarkedAsUnread`). "Mark all
      as read" and last-seen privacy are still missing
- [ ] Reactions: full picker with all server reaction types (paid included),
      reaction lists with reactors, per-chat available reactions, quick
      reaction settings, double-tap on every layout
- [ ] Polls and quizzes: rendering, voting, revoting, results, explanation,
      close/stop, "quiz mode" authoring, anonymous/multiple answers
- [ ] Checklists/todo messages (TDLib 1.9+), task toggling
- [ ] Giveaways and gifts rendering and participation
- [ ] Message effects, animated emoji, premium-only stickers
- [ ] Translation: translate message, translate whole chat, hide original
- [ ] Message TTL / self-destruct timers with countdowns
- [ ] Saved Messages becomes a real bookmarking surface: tags,
      "saved from" attribution, search
- [ ] Topics: forum topic management (create/edit/close/pin), topic search,
      topic notifications, channel comment topics (direct-message topics)
- [ ] Message details: edit date, permalink,
      copy link, save media
- [~] Per-message actions: delete for me only (Telegram `revoke = false`,
      MAX `forMe = true`, VK `delete_for_all = 0`), copy link on Telegram
      through TDLib `getMessageLink`, and selectable text. Report is still
      missing
- [ ] Unread divider, unread jump button and "new messages" marker everywhere
- [ ] Draft center: all unfinished drafts grouped per account with jump-to-chat
- [ ] Media captions and text-only edits across providers

---

## 3. Chats, groups, channels and communities

- [ ] Folder management inside the app: create/edit/delete server folders,
      include/exclude rules, folder invite links, folder order sync
- [ ] Chat actions: leave, mute schedule, clear history, delete chat,
      block/unblock, report
- [ ] Chat info screen: description, invite links, members preview, media,
      shared links, settings, permissions
- [ ] Contact management: add/edit/delete, contact notes, birthdays,
      phone-number privacy, sync to system contacts
- [ ] Blocked users list with unblock, report and block-from-profile actions
- [ ] Group creation and upgrade flows, invite links, join requests,
      approval queues, "join as" and anonymous admins
- [ ] Group administration UI: members, admins, restricted users, banned
      lists, slow mode, permissions matrix, invite links, group sticker set,
      linked chat, discussion group, anti-spam settings
- [ ] Channel administration UI: post rights, signatures, discussion,
      statistics, scheduled posts, reactions, "send as" identity
- [ ] Channel comments as a first-class surface (threads, comment counts,
      quick reactions, moderation)
- [ ] Community/space features (TDLib communities, if the server exposes them)
- [ ] Stories: feed, viewer, reactions, replies, highlights, close friends,
      story privacy, posting stories (photo/video), story notifications
- [ ] Boosts and channel level, "boost channel" action
- [ ] Statistics for channels/groups with charts
- [ ] Group/channel invite-link management: create, revoke, expiry, usage limits
- [ ] Join-request queue with approve/decline and bulk actions
- [ ] Admin log / recent actions for groups and channels
- [ ] Member search, role filters and bulk member actions
- [ ] Group permissions preview before joining and after restriction changes
- [ ] Chat transfer/ownership flows where the provider supports them
- [ ] Discussion-group binding and comment moderation from the channel

---

## 4. Media, files and galleries

- [x] Staggered/justified media grid (no holes, Telegram-style row packing)
- [~] Fullscreen photo/video viewer with save and share
- [~] Gallery pager: swipe, thumbnails, arrows, per-item download, pinch
      zoom, panning, double-tap zoom and a caption overlay. Shared-element
      transitions are still missing
- [ ] Upload pipeline: progress, pause/resume, cancel, retry, background
      uploads, compression settings (photo quality, video quality), EXIF strip
- [~] Upload staging: reorder, remove and caption attachments in the review
      modal. Mixed albums, per-file progress and failure isolation are still
      missing
- [ ] Download manager: queue, progress, cancel, open-with, share, save to
      device, "download all media in chat"
- [ ] Photo editor: crop/rotate/filters/draw/text before sending
- [ ] Video editor basics: trim, mute, compress
- [ ] Audio player: waveform, speed, seek, queue, background playback,
      lock-screen controls
- [ ] Video streaming with adaptive quality instead of downloading whole files
- [ ] Voice message recording UX: live waveform
- [ ] Sticker, emoji and GIF picker: full sticker sets,
      animated previews, search by emoji, pack management, favorite packs,
      cross-provider sticker conversion (already partly supported)
- [ ] Media albums: preview grid, reordering, mixed photo/video, captions
- [ ] File-aware messages: file type icons, "open with", in-app preview for
      PDFs/text/archives
- [ ] Media spoilers, self-destruct media, protected content rules
- [ ] Location: static and live sharing, map picker improvements, live
      location updates, geochat support if feasible
- [ ] Wallpapers: per-chat wallpapers, gradient editor, pattern intensity,
      animated wallpapers, wallpaper sharing
- [ ] Media memory cache with downsampling and prefetch; no full-resolution
      decode for list thumbnails
- [ ] Attachment cleanup policy for outbox files, temp recordings and exports
- [ ] Media permission handling: partial photo access on Android, scoped
      storage, clear denied-state UX
- [ ] Drag files onto the desktop chat and paste images from the clipboard into
      the composer on both platforms

---

## 5. Security, privacy and data ownership

- [ ] Two-step verification management: password, recovery email, hint
- [ ] Login email and login-alert settings
- [ ] Active sessions: already listed; add last-active details, session naming,
      terminate-on-logout and suspicious-session warnings
- [ ] Privacy settings: last seen, profile photo, phone number, forwarded
      messages, groups/channels, read receipts and password
- [ ] Blocked users, sensitive content, content protection and
      "restrict saving content" enforcement
- [ ] Local database encryption and secure key storage on Android/desktop
- [ ] Secure token storage for MAX/VK; never place tokens in plain settings JSON
- [ ] Certificate/transport hardening and dependency vulnerability review
- [ ] Account deletion and "remove local data" flow with explicit scope
- [ ] Permission audit: camera, microphone, photos, notifications, contacts,
      storage; request only when the feature is used
- [ ] Deep-link validation and untrusted-input hardening across providers
- [ ] Threat model and secure-storage documentation

---

## 6. Telegram depth

- [ ] Bot platform: inline bots, bot commands and menus, callback buttons,
      keyboard buttons, web apps/mini apps with auth, payments, invoices,
      shipping, bot descriptions and "start" deep links
- [ ] Web apps: full-screen mini apps, storage, theme propagation, closing
      confirmation, sharing, auth via `initData`
- [ ] Business features: business accounts, quick replies, greeting/farewell
      messages, away messages, business hours, linked bots, chat folders
- [ ] Gifts, stars, premium: send gifts, buy stars/premium (where allowed),
      gift collections, custom emoji status
- [~] Session and security: server-side active session list with per-device
      and "all other sessions" termination (Telegram). 2FA password management,
      passcode lock (app-level and per-chat), login email and secret chat key
      verification are still missing
- [~] Network: traffic statistics and low/medium/high data-saver profiles,
      both stored locally instead of on the server. Reset and manual
      customization exist; per-network rules and detailed connection state are
      still missing
- [ ] Deep links for users, chats, messages, folders, themes, stickers,
      bots and web apps; QR codes for profiles/chats
- [ ] Storage and data: data usage statistics, roaming warnings, video
      autoplay policy
- [~] Accessibility settings parity: an auto-play animation toggle (Data and
      network) and a "reduce motion" switch (Accessibility) exist. Readable
      labels for every icon-only control are still missing
- [ ] Telegram Premium account surface: limits, badges, premium stickers and
      reactions, without blocking core free features
- [ ] Telegram Passport and secure document flows, if still supported
- [ ] Channel giveaways, boosts and star reactions
- [ ] Bot payments and invoice review with clear confirmation boundaries
- [ ] Login/registration edge cases: multiple accounts, phone migration,
      number change, QR login, email verification and recovery

---

## 7. MAX parity

- [ ] Verify and complete sign-in flows (SMS/QR/token refresh, 2FA)
- [ ] Full chat list: folders/tabs, pinned, archived, unread, muted, sorting
- [ ] Message types: text, media, albums, files, stickers, voice, video notes,
      locations, contacts, polls, forwarded, replies, edits, deletions
- [ ] Sending pipeline: replies, forwards, edits, deletes, reactions, pins,
      typing, read receipts, drafts
- [ ] Chats: create groups/channels, invites, join requests, member lists,
      admin actions, permissions, descriptions, avatars
- [ ] Profiles: own profile editing, avatars, bios, usernames, phone privacy
- [ ] Search: chats, messages, global users/channels
- [ ] Media: upload/download with progress, caching, viewers, sticker packs
- [ ] Notifications and per-chat mutes (server-side if available)
- [ ] Bots and mini apps (MAX-specific)
- [ ] Offline behavior and reconnect/monitoring hardening
- [ ] Protocol coverage tests against real captured MAX frames
- [ ] Feature matrix documenting unsupported MAX operations instead of silently
      showing generic errors
- [ ] MAX-specific attachment and sticker conversion rules, including Lottie
      fallbacks and size limits
- [ ] Rate-limit and flood-wait handling with user-visible retry timing

---

## 8. VK parity

- [ ] OAuth/token flow with secure storage and refresh
- [ ] Long polling with catch-up after reconnect; fallback to polling
- [ ] Conversations and messages: text, attachments, forwards, replies,
      edits, deletions, read state, typing
- [ ] Media: photos, videos, documents, audio, voice, stickers, GIFs
- [ ] Profiles: users, mutual friends, followers
- [ ] Search: messages, users, communities
- [ ] Keyboard/bot messages and mini apps
- [ ] Stories and clips (read/creation if supported)
- [ ] Notification mutes and settings parity
- [ ] Token expiry, captcha and validation flows with clear recovery
- [ ] VK-specific pagination and long-poll error mapping tests
- [ ] Feature matrix documenting unsupported VK operations

---

## 9. UX and platform polish

### Desktop

- [~] Keyboard-driven UX: shortcuts for search, next/previous chat, reply,
      edit, jump to unread, send, new chat, settings
- [ ] Command palette / quick switcher across accounts, chats and messages
- [~] Tray icon with unread badge and quick actions (open, mute, quit)
- [ ] Native context menus for messages, chats, media and text fields
- [ ] Drag and drop: files into the composer, chats into folders, text into
      the composer, images into albums
- [ ] System integration: "send to" from the file manager, share targets,
      desktop notifications with reply/read actions

### Android

- [ ] Share sheet target for text, images and files
- [ ] Notification actions: reply, mark read, mute, open chat
- [~] Notification grouping per account and chat, avatars where supported
      (accounts and chats grouped with a silent summary; avatars not yet)
- [ ] App shortcuts: recent chats
- [ ] Back-gesture handling per navigation level, predictive back support
- [ ] Battery and Doze behavior for long-poll/WebSocket providers
- [ ] Foreground-service policy for long-running uploads if background limits
      require it

### Shared UX

- [ ] Empty states, skeletons and error states for every screen
- [x] Offline/connecting banner with per-account state and a "retry now" action
- [ ] Global error/toast host; never lose an error because a screen recomposed
- [ ] Undo for destructive actions: delete, archive, leave, clear history
- [~] Selection-mode polish: select all of the loaded history, a selected
      row count and forward/delete batch actions are done; range (shift-click)
      select and archive/mute batch actions are still missing
- [ ] Motion design pass: chat transitions (already shared-element), message
      appearance, list animations, reduced-motion mode
- [ ] Accessibility: semantics, focus order, screen-reader labels and keyboard focus rings
- [x] Settings search
- [ ] Onboarding: account-provider chooser, permission explanations and a
      first-run tour that does not block messaging
- [x] About screen: version, licenses, source link

---

## 10. Quality, performance and release

- [ ] Performance budgets and CI checks: cold start, chat open time, list
      scroll FPS, memory ceiling, APK/desktop bundle size
- [ ] Image and media memory budget with downsampling and prefetch
- [ ] Baseline profiles and startup tracing on Android
- [ ] Compose recomposition audit on chat list, history and profiles; stable
      models and `derivedStateOf` where it matters
- [ ] Database query budgets, indexes and pagination checks on large accounts
- [ ] Test pyramid: unit tests for controllers/parsers, fake-provider UI tests,
      golden images, protocol fixtures and a small TDLib integration harness
- [ ] Deterministic, recorded protocol fixtures for MAX/VK transports
- [ ] Release engineering: reproducible builds, signing, versioning, update
      channels (stable/beta/test), changelog generation, crash symbolication
- [ ] CI matrix for Linux/Windows/macOS + Android, with caching and nightly
      builds; automated release artifacts in `dist/`
- [ ] Security review: secrets handling, storage encryption, permission
      requests, deep-link validation, dependency audit
- [ ] Documentation: architecture guide, provider-authoring guide, testing
      guide, user FAQ; keep `AGENTS.md` and this roadmap current
- [x] Network diagnostics: connection state, retry timeline, last error and
      protocol dump shortcut per account (Diagnostics → Network; the protocol
      dump toggle is global rather than per account)
- [ ] Crash symbolication and optional remote crash upload behind explicit
      consent, if a relay is ever provisioned
- [ ] Backups: versioned, encrypted, import preview, conflict resolution and a
      restore dry run
- [~] Data integrity checks on startup: unreadable settings/outbox files are
      quarantined and listed in Diagnostics → Storage health, and queue entries
      of removed accounts are pruned. Cache checks and automatic repair are
      still missing

---

## 11. Reworks and technical debt (do alongside features, not after)

These are not polish items. They are the work that keeps the app maintainable
and prevents the next feature from making the codebase harder to change.

### Screen and file decomposition

- [ ] Split `DialogScreen.kt` (~4.5k lines) into a state holder plus focused
      composables: header, message list, composer shell, banners, menus,
      overlays, attachments and dialogs.
- [ ] Split `ProfileScreen.kt` (~1.6k lines) and `SettingsScreen.kt` (~1.8k
      lines) into per-tab and per-section files; keep navigation wiring thin.
- [ ] Split provider sessions (`TelegramSession.kt` ~7.1k lines,
      `MaxSession.kt` ~4.4k lines, `VkSession.kt` ~1.5k lines) by concern:
      auth, dialogs, history, sending, media, profiles, search, settings.
- [ ] Move screen state out of composable-local `remember` blocks into explicit
      state holders with testable reducers/actions.
- [ ] Replace ad-hoc boolean state clusters (dialogs, pickers, menus) with
      sealed UI-state models so invalid combinations cannot exist.
- [ ] Establish a component library boundary: `ui/components` must not reach
      into `features/*`; screens compose components, never the reverse.

### Architecture and dependencies

- [ ] Break provider modules' dependency on the UI `shared` module. Extract a
      neutral `platform`/`runtime` module for storage, URL fetch, media decode
      and clock so `telegram`, `max` and `vk` depend only on `provider` plus
      that runtime.
- [ ] Replace global singletons (`ApiManager`, `OutboxManager`,
      `AppSettingsStore`, `AppLog`, `CacheStore`, `CrashReports`) with an app
      container that can be constructed for tests and previews.
- [ ] Introduce one repository layer for accounts, dialogs, history, media and
      settings so screens never combine several globals directly.
- [ ] Define explicit dispatcher ownership for TDLib, WebSocket, database and
      file I/O; audit blocking calls on the main thread.
- [ ] Make session lifecycle idempotent: start/close/logout/remove-account must
      be safe to call twice and in any order, with contract tests for each.

### Data, caching and offline

- [ ] Unify cache identity and media loading behind one repository API; remove
      direct `loadMediaBytes`/`loadFile` calls from composables.
- [ ] Define cache invalidation rules for edits, deletions, forward merges,
      account removal and logout.
- [ ] Add a real local database source of truth for dialog list and recent
      message windows, with flows that survive a cold start without a network.
- [ ] Add a durable outbox worker abstraction so retries can move to a platform
      background scheduler without rewriting UI.
- [ ] Add cleanup for orphaned outbox attachment paths, stale thumbnails and
      abandoned uploads.
- [~] Add quota and conflict handling for settings backup import: the schema
      downgrade refusal and change preview are done, per-field conflict
      resolution and a size quota are still missing.

### Networking and providers

- [ ] One reconnect policy shared by MAX and VK: exponential backoff, jitter,
      online/offline signals and visible state, without duplicate polling loops.
- [ ] One pagination contract: cursors, gaps, deduplication and end-of-history
      semantics identical across providers.
- [ ] One error mapping table per provider with fixtures; no raw transport
      exception should reach the UI.
- [ ] Request cancellation and timeouts everywhere, including media uploads and
      long-poll shutdown.
- [ ] Provider capability model that the UI reads before showing actions
      (reactions, polls, topics, folders, drafts, scheduling) instead of
      provider-id checks.
- [ ] Secure token/session migration for MAX and VK away from plain settings.

### UI and rendering

- [ ] Theme tokens and a typography/color API used by every screen; remove
      hardcoded colors and one-off text styles.
- [ ] Stable immutable UI models: avoid passing large mutable provider objects
      through recomposition; use `Immutable`/persistent list types where useful.
- [ ] Paging and list performance rework: stable keys, prefetch distance,
      content-type-aware lazy items and no full-list recomposition on one row.
- [ ] Media renderer abstraction: one component per media kind shared by chat,
      profiles, shared media and the viewer.
- [ ] Shared-element transition registry instead of per-screen key strings.

### Tooling, testing and docs

- [ ] Contract test expansion with fake transport and fixture-based provider
      mapping tests.
- [ ] A fake `AccountSession`/`MessengerProvider` for UI tests, previews and
      offline development.
- [ ] Keep `AGENTS.md` as the source of truth for invariants; add a short
      architecture decision record for every cross-cutting rework.

---

## 12. Essential missing surfaces

These are user-facing gaps that are easy to forget while deep in protocol work.

- [ ] Account onboarding and recovery: provider chooser, QR/SMS/token flows,
      clear error recovery, "remove account" with local-data scope
- [x] Settings search over every row, including screens that were not opened
- [ ] Notification center/inbox: mentions, reactions, replies and missed
      notifications grouped by chat
- [ ] folder management from the chat list, not only Settings
- [ ] Drafts center and scheduled-message center
- [ ] Saved Messages as a bookmark surface with tags and "saved from"
- [ ] Contact book with add/edit/remove and system-contacts sync
- [ ] Blocked users and reported-content history
- [~] Data and storage center: cache size, clear cache and per-account
      "keep media" exist in Settings → Storage; usage by chat, clear cache per
      chat and a per-network download policy are still missing
- [~] Backup and restore: export/import with a change preview and refusal of
      files from a newer build; encryption and conflict merging are still
      missing
- [x] About, licenses, source link
- [ ] Diagnostics entry points from errors ("copy debug info", "open logs")
- [x] Offline mode surface: queued count, retry all, discard all and per-account
      connection state (Accounts screen, next to the account list)
- [ ] Global error center so failures do not disappear with a screen
- [ ] Onboarding permission explanations for notifications, camera, microphone
      and photos
- [ ] Empty/error/loading states for every list and settings section
- [x] Keyboard shortcut cheat sheet on desktop

---

## Suggested sequencing

1. **Keep foundations green**: contract tests, fake provider, protocol fixtures,
   golden coverage and error envelope.
2. **Core messaging polish** for Telegram: rich text, schedules, forwarding
   picker, in-chat search, reactions, polls, translation and message links.
3. **Chats/groups/channels administration**, archive, contacts, comments and
   topics.
4. **Security and privacy**: app lock, per-chat lock, secure token storage,
   proxy, 2FA/session management and local-data controls.
5. **Media pipeline**: upload/download managers, gallery, audio/video playback,
   editors and storage policy.
6. **Telegram depth**: bots, mini apps, business, gifts/premium, deep links and
   push notifications with the app closed.
7. **MAX then VK parity**, reusing the provider contract tests and capability
   model.
8. **Reworks in parallel**: split giant screens/sessions, break provider→UI
   dependencies, introduce repositories and a testable app container.
9. **Polish, accessibility, localization, performance and release** —
   continuously, not last.

## Definition of "core messenger done"

A user can install Scrollix, sign in to Telegram (and optionally MAX/VK),
manage several accounts, read and search all their chats including topics and
comments, send every common message type with attachments and formatting,
manage groups and channels, protect local data with a lock and privacy controls,
receive notifications with the app closed, and trust that nothing is lost
offline — on Android and on desktop, with the same settings and a familiar
Telegram-grade experience.
