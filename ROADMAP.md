# Scrollix roadmap

Remaining work only. Shipped features are not tracked here; the git history
and `AGENTS.md` cover them.

**Non-goals:** voice and video calls, call UI, call history, call notifications
and call-related system integrations.

**Where it stands:** multi-account Telegram/MAX/VK behind `AccountSession`;
chats, history, rich-text composer with schedules, polls, reactions, topics,
profiles, layered search, settings, an offline outbox, local diagnostics and a
test server. MAX and VK lag behind Telegram.

**Order of work:** messaging gaps → chats/groups/channels → security and
privacy → media pipeline → Telegram platform depth → MAX, then VK. Reworks run
alongside; performance, accessibility and release work never stop.

## Messaging

- Comment-thread composers still keep reply text only while open: TDLib has
  no draft for a reply thread, so those replies share the chat draft. Topic
  drafts and cross-device conflict resolution are in place.
- Reply quote selection (replying with a chosen passage) and
  reply-in-another-chat.
- Forwarding into topics.
- Selection: range select by drag / shift-click, and archive/mute batch
  actions.
- Search: paged global results and grouping by account; date ranges on the
  provider side (the presets already narrow the loaded page).
- Last-seen per-contact exceptions: read and preserve the full TDLib rule list
  instead of rewriting it as one base rule.
- Reactions: paid reactions, the full `getMessageAddedReactions` list, and
  double-tap on wide layouts.
- Checklists: authoring, adding tasks to an existing list, "completed by".
- Gift and giveaway rendering and participation.
- Message effects, animated emoji and premium-only stickers.
- Translation: replace the original text in place.
- Secret-chat self-destruct (TTL) picker.
- Topics: notifications, hidden topics, channel comment topics.
- A Report action in the message menu, with reported-content history.
- Unread divider in the topic and thread surfaces.
- MAX/VK: caption editing for media messages.

## Chats, groups and channels

- Folder management: create/edit/delete, include/exclude rules, invite links,
  order sync; also reachable from the chat list, not only Settings.
- Chat actions: leave, mute schedule, clear history, delete chat, block,
  report.
- Chat info: description, invite links, members preview, shared media and
  links, permissions.
- Contacts: add/edit/delete, notes, birthdays, phone privacy, system sync.
- Group creation and upgrade, invite links, join requests, approval queues,
  "join as", anonymous admins.
- Group administration: members, admins, restricted and banned lists, slow
  mode, permissions matrix, group sticker set, linked chat, anti-spam.
- Channel administration: post rights, signatures, discussion, statistics,
  scheduled posts, "send as".
- Channel comments as a first-class surface, including moderation.
- Boosts and channel levels; channel and group statistics.
- Admin log, member search with role filters, bulk member actions, ownership
  transfer.
- Community/space features if TDLib exposes them.

## Media and files

- Upload pipeline: progress, pause/resume, cancel, retry, background uploads,
  compression settings, EXIF stripping.
- Staging: mixed photo/video albums, per-file progress, failure isolation.
- Download manager: queue, progress, cancel, open-with, share, save to device.
- Photo editor (crop/rotate/filters/draw/text) and basic video tools
  (trim/mute/compress).
- Audio player: waveform, speed, queue, background playback, lock-screen
  controls.
- Adaptive video streaming instead of downloading whole files.
- Live waveform while recording a voice note.
- Sticker/emoji/GIF picker completeness: full sets, animated previews, emoji
  search.
- File-aware messages: type icons, "open with", in-app preview for PDFs, text
  and archives.
- Media spoilers, self-destruct media, protected-content rules.
- Location: map picker improvements, live updates, geochats if feasible.
- Wallpapers: per-chat, gradient editor, patterns, animated wallpapers,
  sharing.
- Attachment cleanup for outbox files, temp recordings and exports.
- Media permissions: partial photo access on Android, scoped storage, denied
  state UX.
- Desktop drag-and-drop of files and text; clipboard image paste on both
  platforms.

## Security and privacy

- Two-step verification management: password, recovery email, hint.
- Login email and login-alert settings.
- Sessions: last-active details, naming, terminate-on-logout, suspicious
  session warnings.
- Privacy: profile photo, phone number, forwarded messages, groups/channels,
  read receipts.
- Blocked users, sensitive content; enforce "restrict saving content".
- Local database encryption and secure key storage on Android and desktop.
- Secure token storage and migration for MAX/VK, away from plain settings JSON.
- Transport hardening and a dependency vulnerability review.
- Account deletion and "remove local data" with an explicit scope.
- Permission audit: camera, microphone, photos, notifications, contacts,
  storage; request only when the feature is used.
- Deep-link validation and untrusted-input hardening across providers.
- Threat model and secure-storage documentation.

## Telegram depth

- Bots: inline bots, commands and menus, callback buttons, reply keyboards,
  mini-app auth, payments/invoices/shipping, bot descriptions, "start" links.
- Web apps: fullscreen, storage, theme propagation, closing confirmation,
  sharing, `initData`.
- Business features: business accounts, quick replies, greeting/farewell, away
  messages, business hours, linked bots.
- Gifts, stars and premium surfaces without blocking free features.
- Passcode lock (app-level and per-chat) and secret-chat key verification.
- Network: per-network data rules and detailed connection state.
- Deep links for users, chats, folders, themes, stickers, bots and web apps;
  QR codes for profiles and chats.
- Storage and data: roaming warnings, video autoplay policy.
- Premium: limits, badges, premium stickers and reactions.
- Telegram Passport and secure document flows, if still supported.
- Login edge cases: phone migration, number change, QR login, email
  verification, recovery.
- Push notifications with the app closed (today they need a live process).

## MAX

- Sign-in: complete SMS/QR/token refresh/2FA.
- Chat list: folders, pinned, archived, muted, sorting.
- Remaining message types and sending gaps: replies, forwards, edits, deletes,
  reactions, pins, typing, read state, drafts.
- Search parity.
- Media upload/download progress, caching, viewers, sticker packs.
- Notifications and per-chat mutes.
- Bots and mini apps.
- Offline and reconnect hardening.
- Protocol fixtures from captured frames; flood-wait and rate-limit UX; a
  feature matrix of unsupported operations; attachment/sticker conversion
  rules.

## VK

- OAuth/token flow with secure storage and refresh.
- Long polling with catch-up and fallback; pagination and long-poll error
  tests.
- Conversation and message gaps; media; profiles; search; bot keyboards.
- Stories and clips where supported.
- Notification mutes and settings parity.
- Token expiry, captcha and validation recovery.
- A feature matrix of unsupported operations.

## UX and platform

**Desktop**

- Command palette / quick switcher across accounts, chats and messages.
- Native context menus for messages, chats, media and text fields.
- Drag and drop into the composer, into folders and into albums.
- System integration: "send to", share targets, notification reply/read
  actions.
- Finish the keyboard shortcut set and tray actions.

**Android**

- Share-sheet target for text, images and files.
- Notification actions (reply, mark read, mute, open) and avatars.
- App shortcuts for recent chats.
- Predictive back and per-level back handling.
- Battery/Doze policy for long-poll and WebSocket providers.
- Foreground-service policy for long uploads if background limits require it.

## Engineering

**Testing**

- Contract mapping fixtures: DTO → model for messages, media, folders,
  reactions, profiles, errors and pagination.
- A fake `AccountSession`/`MessengerProvider` for UI tests and previews.
- Recorded transport fixtures for MAX/VK instead of hand-built JSON.
- A TDLib integration smoke suite against the test server, env-gated.

**Performance**

- Budgets and CI checks: cold start, chat open, scroll FPS, memory, bundle
  size; baseline profiles and startup tracing.
- Recomposition audit on the chat list, history and profiles; stable models.
- Database query budgets, indexes and pagination checks on large accounts.
- Media memory budget: downsampling and prefetch, no full-resolution decodes
  for list thumbnails.
- List/paging rework: stable keys, prefetch distance, content-aware items.

**Data and offline**

- One repository/API for cache identity and media; remove direct
  `loadMediaBytes`/`loadFile` calls from composables.
- Cache invalidation rules for edits, deletions, forward merges, account
  removal and logout.
- A local database as the source of truth for the dialog list and recent
  message windows, usable on a cold start without network.
- A durable outbox worker that can move to a platform background scheduler.
- Cleanup for orphaned outbox attachment paths, stale thumbnails and abandoned
  uploads.
- Startup integrity checks and automatic repair for the cache.

**Architecture**

- Break the provider → UI dependency: a neutral `platform`/`runtime` module for
  storage, URL fetch, media decode and clock.
- Replace global singletons (`ApiManager`, `OutboxManager`, `AppSettingsStore`,
  `AppLog`, `CacheStore`, `CrashReports`) with an injectable app container and
  a repository layer.
- Explicit dispatcher ownership for TDLib, WebSocket, database and file I/O;
  audit main-thread blocking.
- Idempotent session lifecycle (start/close/logout/remove) with contract tests.
- One reconnect policy for MAX/VK, one pagination contract, per-provider error
  mapping fixtures, cancellation and timeouts everywhere.
- A capability model for actions instead of provider-id checks.
- Continue splitting large screens and settings sections; keep provider
  sessions split by concern as they grow.

**Release and docs**

- Reproducible builds, signing, versioning, update channels, changelog and
  crash symbolication.
- CI matrix for Linux/Windows/macOS/Android with caching, nightly builds and
  artifacts in `dist/`.
- Architecture, provider-authoring, testing and user documentation; an ADR for
  each cross-cutting rework.
- Optional remote crash upload behind explicit consent, if a relay exists.

## Done means

Sign in to Telegram (and optionally MAX/VK), manage accounts, read and search
every chat including topics and comments, send every common message type with
formatting, manage groups and channels, protect local data, receive
notifications with the app closed, and never lose a message offline — on
Android and desktop.
