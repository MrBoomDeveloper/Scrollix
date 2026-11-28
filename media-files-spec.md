# Media and files — completion spec

Source: `ROADMAP.md` → **Media and files**. The request was to finish everything
that section still lists as unfinished. This document is the agreed scope,
constraints and design; no code is written yet.

Status legend: **done** (already in the tree), **partial**, **missing**.

---

## 1. Scope

| Roadmap item | State today | Phase |
| --- | --- | --- |
| Upload pipeline: progress, pause/resume, cancel, retry, background uploads, compression settings, EXIF stripping | partial | 2 |
| Staging: mixed photo/video albums, per-file progress, failure isolation | partial | 2 |
| Download manager: one queue shared by chat, viewer and a Downloads tab | missing | 3 |
| Photo editor (crop/rotate/filters/draw/text) | missing | 1 |
| Basic video tools (trim/mute) | missing | 1 |
| Audio player: waveform, speed, background playback, lock-screen controls | partial | 4 |
| Adaptive video streaming instead of whole-file downloads | missing | 4 |
| Live waveform while recording a voice note | **done** (`RecordingWaveform.kt`, `appendWaveformLevel`) | — |
| File-aware messages: type icons | partial (text label `PDF`, `ZIP`) | 5 |
| Media spoilers | **done** (`MediaSpoiler.kt`, composer spoiler) | — |
| Self-destruct media | missing | 5 |
| Location: map picker improvements, live updates | **done** (OSM picker, live location with stop) | — |
| Wallpapers: per-chat via Telegram API setters | missing (display only) | 5 |
| Cleanup for abandoned uploads / outbox attachments after a crash | **done** (Storage health) | 2 (extend to editor/trim temp files) |
| Desktop drag-and-drop of files and text; clipboard image paste | drag-and-drop missing, clipboard paste **done** (`readClipboardImages` both platforms) | 5 |

Everything above ships, in the phase order chosen: **editor → uploads →
downloads → audio/streaming → small items**.

### Non-goals

- Secret chats (Telegram's per-message TTL lives there). View-once is the only
  self-destructing surface this spec adds.
- An audio queue / next–previous across a chat. Background playback is for the
  single track being played.
- Server-side data-saver presets. Data saver stays local
  (`AppSettings.dataSaving` + `DataUsageStore`).
- Gradle files. Dependencies go into `module.yaml`, versions into
  `libs.versions.toml`.

---

## 2. Verified current state

- Sending: `DialogScreenViewModel.addAttachments` caps at `MaxAlbumAttachments`;
  rows live in `pendingAttachments` and are reviewed in
  `shared/src/features/dialog/composer/AttachmentReview.kt`; sending goes through
  `OutboxManager` → `AccountSession.sendFiles` → `TelegramSessionMessages.sendFilesImpl`.
- Upload progress: `UploadProgressStore` (path → fraction) fed from
  `TelegramSessionUpdates`; pending bubbles read it in
  `DialogScreenViewModelContent` and `MessageBubbleParts`.
- Upload retry: `OutboxManager` backoff for retryable `SessionException`s. There
  is no pause, no cancel, no background service, no compression, no EXIF step.
- Download: every surface calls `session.loadMediaBytes(...)` and polls
  `session.mediaDownloadProgress(fileId)`; `MessageFileCard` and
  `FullscreenGalleryViewer` each start their own job and call
  `cancelMediaDownload`. No shared queue, no Downloads screen.
- Video: `VideoPlayer` downloads the whole file through `loadMediaBytes` and
  hands the bytes to `VideoPlayback.load(bytes, …)`. Android is
  `MediaPlayer` + `TextureView` (`VideoPlayback.android.kt`); desktop is
  `FFmpegFrameGrabber` (`VideoPlayback.jvm.kt`). Quality picker already exists.
- Audio: `VoiceMessage.kt` has waveform, seek, speed 0.5×–2×;
  `VoicePlayer` expect/actual, no service, no lock-screen controls.
- File cards: `MessageFileCard.kt` draws a 56 dp box with
  `media.fileTypeLabel()` (extension text), then download/preview actions.
- Wallpapers: `DialogWallpaper.kt` renders `dialog.wallpaper` and
  `AppSettingsStore`'s custom image; no picker, no setter calls.
- Endanger checks: `selfDestructType = null` at all send sites
  (`TelegramSessionMessages.kt` 232/282/304/525).
- Clipboard image paste is implemented on both platforms; the desktop
  drag-and-drop modifier is used nowhere (`dragAndDropTarget` has no matches).

Reference implementation inspected for streaming:
`/home/mrboomdev/TelegramAndroidSource` — `FileLoadOperation` fetches chunks and
notifies `FileLoadOperationStream` listeners,
`FileStreamLoadOperation` is a media3 `DataSource` that reads the growing local
file, requests the needed offset and blocks on a latch until bytes arrive.

---

## 3. Decisions from the interview

| Topic | Decision |
| --- | --- |
| Scope | Everything still open in the section, phased. |
| Editor tools | Crop/rotate/flip, filters, draw, text, stickers/emoji, blur/pixelate, aspect presets (1:1, 4:5, 16:9). |
| Entry points | Attachment review row only (photos and videos). Picking N files opens review first; nothing is forced. |
| Platforms | Android and desktop, one shared UI. |
| Editor UX | Full-screen: image in the middle, tool tabs under it, Apply/Cancel on top, undo/redo always visible. |
| Edit model | Non-destructive until send; reopening restores layers. |
| Re-encode | Preserve container/quality where possible, strip GPS and device-identifying EXIF. |
| Video tools | Trim with handles + frame previews, mute toggle. |
| Trim engine | Exact re-encode (no keyframe snapping). |
| Upload pause | Real pause with offset (see the provider constraint in §6.1). |
| Background uploads | Android foreground service with overall progress and cancel. |
| Compression | Per-send only, in the review sheet. |
| Downloads UX | Shared queue **plus** a Downloads screen reachable from Storage settings. |
| Album failure | Per-file retry; the rest of the album still sends. |
| Audio | Background + notification (lock-screen play/pause/seek/speed); desktop stays inline. |
| Streaming | Mirror Telegram: adopt **media3 ExoPlayer** on Android with a TDLib-backed `DataSource`. Desktop reads the growing file with FFmpeg. |
| View-once | Per-file toggle in the review sheet; hidden when the provider says unsupported. |
| Wallpapers | Server gradients/patterns **plus** own photo; apply per chat, with reset. |
| File icons | Icon replaces the extension label; extension moves to the subtitle line. |
| Phasing | Editor → uploads → downloads → audio/streaming → small items. |

---

## 4. Phase 1 — photo editor and video tools

### 4.1 Editor entry and shell

- Entry: an edit button on each photo/video row in `AttachmentReview`. Tapping
  it opens the full-screen `PhotoEditorDialog`; closing it returns to review
  with the row updated.
- Full-screen dialog (not a route): the composer and review stay mounted, so
  back returns to review, and a recording-in-progress or a draft is untouched.
- Layout: top bar (Cancel, title, Reset, Apply), image canvas, tool tab row
  (Crop · Filter · Draw · Text · Stickers · Blur), contextual panel above the
  tabs (brush colors, font size, filter list, aspect chips), undo/redo buttons
  pinned next to Apply.
- `reduceMotion` disables only decorative transitions (tab cross-fades); the
  apply progress stays.

### 4.2 Non-destructive document

`EditorDocument` (pure data, in common code) holds:

```
sourcePath, sourceWidth, sourceHeight, mimeType
crop: CropRect?          // normalized 0..1 in source space, plus rotation
rotationQuarterTurns, flipH, flipV
filter: FilterPreset
layers: List<EditorLayer> // DrawStroke | TextLayer | StickerLayer | BlurRegion
undoStack, redoStack      // snapshots of the fields above
```

- Every op appends a snapshot (same idea as `ComposerHistory`), so undo/redo is
  uniform and cheap; bitmaps are never copied per edit.
- Layers store **normalized** coordinates (0..1 of the cropped image), so a
  later crop or rotate keeps them in place. Text/stickers also store size in
  normalized units, rotation in degrees, and color.
- Re-opening the editor for a row restores `EditorDocument` from the pending
  attachment state; the original file is never modified.
- Bake happens once, on Apply or on send (see §4.5).

### 4.3 Tools

- **Crop**: draggable corners, rule-of-thirds grid, aspect chips (Free, 1:1,
  4:5, 16:9), rotate 90° left/right, flip H/V. Pure math in
  `cropRectToSource(...)` (unit-tested, including rotation order).
- **Filters**: fixed preset list (None, Warm, Cool, Mono, High contrast,
  Fade, Vintage) expressed as 4×5 color matrices in common code, previewed with
  `ColorFilter.colorMatrix`, baked with the same matrix so preview and output
  match. No LUT assets, no per-pixel Kotlin in the preview.
- **Draw**: freehand brush, 6-color palette + custom color picker, three
  widths, eraser (removes whole strokes, not pixels), strokes stored as point
  lists.
- **Text**: tap to place, inline editing, font size, color, bold/italic,
  drag to move, pinch to scale and rotate.
- **Stickers/emoji**: reuses `EmojiCatalog` glyphs and the existing custom-emoji
  assets; inserted as bitmap layers, movable/scalable.
- **Blur/pixelate**: brush-masked region with an adjustable strength; blur is a
  stack blur over the masked area, pixelate blocks the pixels. Pure Kotlin
  implementation in common code (no RenderScript; it is deprecated).
- **Aspect presets** double as the crop chips; no separate tool.

### 4.4 Platform baking

`expect` API in `shared/src/platform/media/ImageEditor.kt`:

```
expect fun decodeForEditing(path: String, maxEdge: Int = 4096): EditingBitmap?
expect fun bakeImage(document: EditorDocument, target: ImageFormat): BakeResult
```

- Android actual: `BitmapFactory` with `inSampleSize` (long edge capped at
  4096 px), `Bitmap` canvas, `ExifInterface` only to read orientation, output
  through `Bitmap.compress` (JPEG 95; PNG when the source has alpha).
- Desktop actual: `ImageIO.read`, `BufferedImage`/`Graphics2D`, matrices via
  `ColorConvertOp`, output through `ImageIO.write`.
- Blur/draw/text are applied in shared code on an `IntArray`/pixel buffer, so
  both platforms run the same algorithm and the pure parts are testable.
- Output goes to the app's staging directory as
  `scrollix-edit-<uuid>.<ext>`; the review row points at it.
- Memory: decode is capped at 4096 px on the long edge and bake uses the same
  cap. The panel shows a one-line warning for very large sources; there is no
  silent downscale of an untouched photo (untouched photos are not baked).
- Errors: decode failure → row keeps the original and shows "Couldn't edit this
  photo"; bake failure → editor stays open with the error inline; no partial
  file is left behind (temp file deleted on failure).

### 4.5 Encoding rules

- Edited photos are baked and **always** stripped of EXIF GPS, `Make`,
  `Model`, `Software`, `SerialNumber`; orientation is burned into pixels.
- Photos that were *not* edited keep their bytes, except that every outgoing
  JPEG goes through the sanitizer (§6.2), which keeps only `Orientation`,
  `ColorSpace` and `DateTimeOriginal`.
- PNG keeps alpha; WebP is re-encoded to PNG when edited (the desktop encoder
  writes JPEG/PNG only), otherwise left alone.

### 4.6 Video trim/mute

- A "Trim" action on video rows of the review sheet opens `VideoTrimSheet`:
  a thumbnail filmstrip (6 frames extracted off the main thread), two handles,
  current range label, mute switch, Reset, Apply, Cancel.
- Duration limits: none for files, but the range must be ≥ 1 s and the sheet
  warns when the output would exceed the provider's document limit.
- Exact re-encode per platform:
  - Android: `MediaExtractor` + `MediaCodec` + `MediaMuxer` transcode over
    `[start, end)`; audio is re-encoded to AAC, or omitted when muted.
  - Desktop: JavaCV `FFmpegFrameGrabber`/`FFmpegFrameRecorder` over the same
    range (`setStartTime`/`setStopTime`), audio copied when possible, dropped
    when muted.
- Progress: a determinate bar driven by the presented frame timestamp; the
  sheet blocks dismissal while running but offers Cancel, which stops the codec
  and deletes the partial output.
- Output: `scrollix-trim-<uuid>.mp4` in staging; the review row uses it. The
  original file is untouched.
- Disk: refuse to start when free space is below 2× the estimated output and
  show the reason.
- Failure: codec/FFmpeg error → sheet stays, error text, original kept.

### 4.7 Phase 1 file layout (2000-line limit)

```
shared/src/ui/components/photoeditor/EditorDocument.kt      data + undo
shared/src/ui/components/photoeditor/PhotoEditorDialog.kt   shell/top bar
shared/src/ui/components/photoeditor/EditorToolTabs.kt      tabs + panels
shared/src/ui/components/photoeditor/CropOverlay.kt
shared/src/ui/components/photoeditor/FilterPresets.kt
shared/src/ui/components/photoeditor/DrawOverlay.kt
shared/src/ui/components/photoeditor/TextStickerLayers.kt
shared/src/ui/components/photoeditor/BlurBrush.kt
shared/src/ui/components/photoeditor/Baking.kt              shared pixel math
shared/src/platform/media/ImageEditor.kt                    expect
shared/src@android/platform/media/ImageEditor.android.kt
shared/src@jvm/platform/media/ImageEditor.jvm.kt
shared/src/features/dialog/composer/VideoTrimSheet.kt
shared/src/platform/media/VideoTrimmer.kt + actuals
shared/test/ui/components/photoeditor/*Test.kt
```

---

## 5. Phase 2 — uploads, staging, compression, EXIF

### 5.1 Upload pipeline

Provider contract additions (defaults keep MAX/VK compiling and honest):

```
open val supportsUploadPause: Boolean get() = false
suspend fun pauseUpload(path: String): Result<Unit> = success
suspend fun resumeUpload(path: String): Result<Unit> = success
```

- **Pause**: `OutboxEntry` gains `paused: Boolean` and `progress: Float?`
  (last reported fraction, so the row shows where it stopped). Pausing cancels
  the in-flight send coroutine and persists the state; resuming re-invokes the
  same send with the same path.
- **Constraint discovered**: TDLib owns upload state and exposes no
  byte-offset upload API. Telegram therefore implements pause by cancelling the
  upload (implementation must confirm `cancelUploadFile` exists in
  tdl-coroutines 18.0.0; if it does not, cancelling the send is the only
  lever), and resume re-sends the same local path, which continues from the
  parts TDLib already has. Genuine range upload from an arbitrary offset is not
  achievable; the spec's "real pause with offset" means "resume from what the
  provider retained", and the UI must not promise more than that.
- **Cancel**: removes the entry, deletes its staged copies, and calls the
  provider's cancel path. Confirmed through `ConfirmDeleteDialog` because it
  discards a message the user composed.
- **Retry**: per file, keeps the existing backoff; the retry button in the
  message bubble and in the Downloads-style outbox list both call the same
  `OutboxManager` path. No second retry loop is added.
- **Background**: `androidApp/src/UploadService.kt`, a foreground service
  (`foregroundServiceType="dataSync"`) started when the outbox has running
  uploads and the app leaves the foreground. Its notification shows aggregate
  progress, "Pause all", and "Cancel all"; it stops when the queue drains.
  Manifest gains `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC`;
  `POST_NOTIFICATIONS` is already requested by onboarding. Desktop keeps
  uploading while the process lives, no service.
- **Progress**: `UploadProgressStore` stays the single source; the pending
  bubble, review rows and the notification read it.

### 5.2 Staging and albums

- Mixed photo/video albums already work in review; keep the ordering, caption
  and custom-emoji rendering untouched.
- Per-file state in review: `Queued → Uploading (fraction) → Sent / Failed /
  Paused`, with row actions Edit (phase 1), Pause/Resume, Retry, Remove.
- Failure isolation: successful files leave the outbox; the failed row stays
  with its own error and Retry, and the caption/topic/reply target is kept for
  a retry. An album is only "one message" when the provider accepted all parts.
- Removing an unsent row needs no confirmation (nothing is stored yet), except
  when the row has already begun uploading — then it goes through
  `ConfirmDeleteDialog`.

### 5.3 Compression (per send)

- Review sheet gains a "Quality" row with a small sheet:
  - Photo: Original · High (~85, long edge 2560) · Medium (~75, long edge
    1600).
  - Video: Original · 720p · 480p (H.264 re-encode at ~2.5 / 1.2 Mbps).
- Defaults: Original for both; the choice applies to this send only and is not
  persisted (no `AppSettings` field).
- Rows show the effective size ("2.4 MB → 480 KB") when a non-original choice
  is set; a size estimate is computed with the platform encoder's fast pass.
- Compression uses the same platform codecs as the editor (Android
  `Bitmap.compress`, desktop `ImageIO`; Android `MediaCodec`, desktop JavaCV).
- Providers that cannot estimate size simply omit the arrow estimate.

### 5.4 EXIF sanitizer

- `shared/src/platform/media/ExifSanitizer.kt` (pure Kotlin, testable):
  - JPEG: segment walk, drop APP1/EXIF and XMP packets, keep ICC (APP2) and the
    JFIF header, re-insert a minimal EXIF block with `Orientation` and
    `DateTimeOriginal` only.
  - PNG: drop `eXIf`, `iTXt`, `tEXt` chunks that carry GPS/device data.
  - WebP: drop `EXIF` and `XMP ` chunks.
- Applied to every outgoing photo, including untouched originals, right before
  the upload. Tested against fixtures that contain real GPS tags; a JPEG
  without EXIF must pass through byte-identical except for the added
  orientation block.
- Never applied to files sent as documents unless the mime type is an image.

---

## 6. Phase 3 — download manager

### 6.1 Component

`shared/src/data/download/DownloadManager.kt`, one instance per app, keyed by
`(accountId, fileUniqueId)`:

```
request(accountId, fileId, uniqueId, priority): Flow<DownloadState>?
cancel(key); pauseAll(); resumeAll(); observeAll(): StateFlow<List<DownloadTask>>
```

- 2 concurrent downloads across all accounts; FIFO inside a priority band.
- Priorities: `Viewer` (fullscreen/gallery/video player) and `OpenChat`
  preempt queued work; `Background` (prefetch of thumbnails is **not** part of
  this) is the default.
- `DownloadState`: `Queued · Downloading(fraction, bytes, total) · Done · Failed(message) · Cancelled`.
- A screen subscribes to the shared task instead of starting its own job, so
  the chat card, the viewer and the Downloads screen show the same numbers and
  one Cancel stops it for all.
- Provider hook stays `loadMediaBytes` + `mediaDownloadProgress` +
  `cancelMediaDownload`; the manager calls them, screens no longer do.
- Persistence: `downloads.json` (versioned like other persisted JSON, older
  files decode through defaults, newer files are never rewritten) holds the
  queue, including paused entries. Completed entries are capped at 50 and can
  be cleared. On start, entries whose file is already fully cached are marked
  Done without a provider call.
- The manager also enforces `mediaCacheLimitBytes` interaction: a download
  never evicts a file another task is reading.

### 6.2 Downloads screen

- New `Route.Downloads`, reachable from Settings → Storage ("Downloads") and
  from the chat file card's "Show in downloads" overflow action.
- Sections: Active (progress, pause/resume, cancel), Queued, Failed (retry),
  Finished (open, clear from list). "Pause all" and "Clear list" in the top bar;
  clearing the list does not delete cached files, so no confirmation.
- Cancelling a single download is not destructive (cache keeps the partial
  file); cancelling while the file is being deleted is not offered at all.
- Empty state reuses `ScreenStates`.

### 6.3 Integration points

- `MessageFileCard`: download/cancel goes through the manager; the file card
  subscribes to the matching task.
- `FullscreenGalleryViewer` and `FullscreenImageViewer`: saving a file uses the
  manager's task; the progress label is the same fraction.
- `VideoPlayer`: keeps using the streaming source (phase 4) and reports its
  fraction through the manager as a `Viewer`-priority download.
- `ProfileSharedMedia` and search media thumbnails still load eagerly through
  `loadMediaBytes`; they do not create queue entries.

---

## 7. Phase 4 — audio and progressive video

### 7.1 Audio player

- media3 `MediaSessionService` (`shared/src@android/platform/audio/AudioPlaybackService.android.kt`)
  with `ExoPlayer` + `MediaSession`:
  - Notification and lock screen: title (file name / performer), play/pause,
    seek bar, speed label, close.
  - Playback continues when the chat is closed or the app is backgrounded;
    opening another audio file replaces the current one (no queue).
  - Speed changes from the inline player propagate to the session and back
    (0.5×, 1×, 1.5×, 2×; pitch preserved through ExoPlayer's `PlaybackParameters`).
- Desktop: inline `VoicePlayer` only; leaving the chat stops playback, as
  chosen.
- Voice notes play through the same service but keep the inline waveform; the
  notification shows "Voice message" and a seek bar.
- Audio focus: request focus, duck and pause on loss, resume on regain; a phone
  call pauses playback.

### 7.2 Progressive video (Android)

- New dependency: media3 (`libs.versions.toml` alias, `shared/module.yaml`
  `dependencies@android`). Version pinned; no other module needs it.
- `TdlibDataSource : BaseDataSource` (Android source set) mirrors
  `FileStreamLoadOperation`:
  - `open(dataSpec)` resolves the file id, asks `AccountSession` for a
    `MediaStream`, and records the requested offset and length.
  - `read()` copies from the growing local file and blocks on a latch until
    enough bytes exist; `UpdateFile` progress releases the latch.
  - `MediaStream` (common interface):
    `localPath`, `length`, `downloadedPrefix`, `requestRange(from, to)`,
    `awaitBytes(count, timeout)`.
  - Telegram implements it with TDLib (`downloadFile(offset, limit)` range
    requests + the existing `UpdateFile` flow). MAX/VK return `null`, which
    means "fall back to a whole-file download".
- Start threshold: playback begins when 10% or 512 KB is available, whichever
  comes first; a spinner over the poster until then.
- Seeking: inside the downloaded prefix it is instant; outside, the player
  pauses, requests that range, shows "Loading…" and resumes at the target.
- Quality switch restarts the stream on the new file id, keeping the position.
- The existing quality picker, mute, seek bar and error state stay; only the
  data source and the playback engine change.
- `AndroidVideoPlayer` is rewritten on media3; `VideoPlayback` keep its
  expect/actual surface but gains `load(streamSource, …)` alongside
  `load(bytes, …)` for providers without a stream.
- Desktop: `VideoPlayback.jvm` reads the growing file with
  `FFmpegFrameGrabber`, retrying at EOF while TDLib fills it; no temp copy.
- Temp files: streamed files are deleted when the player stops and are listed
  in Storage health cleanup.

### 7.3 Cleanup extension

- Storage health lists and clears: abandoned editor outputs, trim outputs, and
  stream temp files, next to the existing upload/outbox cleanup. Each removal
  goes through `ConfirmDeleteDialog`.

---

## 8. Phase 5 — small items

### 8.1 File type icons

- `MessageFileCard`'s 56 dp box shows a generated Material Symbols icon instead
  of the extension text; the subtitle becomes `PDF · 2.4 MB`.
- Families and colors (fixed palette, readable on both bubble colors): pdf,
  archive, doc, sheet, slide, image, audio, video, text/code, apk, unknown.
- Mapping: mime first, extension second, `unknown` last. Pure function
  `fileTypeFamily(mimeType, fileName)` with a table-driven test.
- Icons are hand-generated `ImageVector`s in `shared/src/ui/icons/filetypes/`
  (AGENTS: no `material-icons-extended`).

### 8.2 View-once / self-destruct

- Provider contract: `open val supportsViewOnce: Boolean get() = false` and
  `sendFiles(..., viewOnce: Boolean = false)`. Telegram reports true only if
  the pinned TDLib version accepts immediate self-destruct in cloud chats
  (verify against the generated API before implementing); MAX/VK report false
  and the toggle is hidden.
- Review row: a view-once toggle for photos and videos. If any row is
  view-once, the send goes as view-once; if the provider cannot send albums
  view-once, the rows are sent one by one (with a note in the sheet).
- Receiving: `isViewOnce` flag on `MessageMedia`; the existing spoiler
  blur/`SpoilerOverlay` is reused with a "Tap to view once" label. After the
  reveal the content is replaced by a "Opened" placeholder and the bytes are
  **never** written to `CacheStore` or the media cache.
- Expiring media received with a TTL shows the remaining time from
  `expiresInSeconds`; there is no countdown timer that silently deletes the
  bubble (the provider removes it).

### 8.3 Per-chat wallpapers

- Chat overflow menu → "Change wallpaper", plus the existing global entry in
  Settings → Chat wallpaper.
- Picker sheet: tabs **Gradients**, **Patterns** (from TDLib backgrounds),
  **Photo** (file picker → crop to the chat aspect), each with a preview.
- Apply calls the provider setter (`setChatTheme`/chat background API — confirm
  exact TDLib method names in tdl-coroutines 18.0.0 before implementing).
  Reset removes the per-chat background.
- A confirmation explains that the background changes for **everyone** in the
  chat, not only locally; Telegram emits a service message.
- Offline: cached background list with a "Couldn't load backgrounds" notice.
- Providers without support hide the action (`supportsChatWallpaper`).

### 8.4 Desktop drag-and-drop

- `Modifier.dragAndDropTarget` on `DialogScreen` (message area and composer)
  and on chat rows in `HomeScreen`.
- Payloads: files (images attach as photos, everything else as files, mixed
  payloads attach all), plain text, and rich text/HTML.
- Dropping files on a chat row opens that chat and shows the attachment review
  (never a silent send). Dropping text into the composer goes through the
  existing `RichPaste` conversion.
- Directories and unsupported payloads are ignored with a short message; the
  drop target highlights while hovering; `reduceMotion` keeps the highlight
  static.
- Clipboard image paste stays as it is.

---

## 9. Provider contract summary

| Member | Default | Telegram | MAX / VK |
| --- | --- | --- | --- |
| `supportsUploadPause`, `pauseUpload`, `resumeUpload` | false / no-op | implemented via TDLib, see §5.1 | false |
| `openMediaStream(fileId, uniqueId)` | `null` | TDLib ranges | `null` (whole-file fallback) |
| `supportsViewOnce`, `viewOnce` on send | false | true if TDLib accepts | false |
| `loadChatBackgrounds`, `setChatBackground`, `removeChatBackground`, `supportsChatWallpaper` | false / empty | TDLib | false |
| `mediaDownloadProgress`, `cancelMediaDownload` | existing | existing | degrade to indeterminate |

Every new failure is a `SessionException`, never a raw exception.

---

## 10. Persistence changes

- `OutboxEntry` (JSON): `paused: Boolean`, `progress: Float?`, and
  `attachment.viewOnce: Boolean`. Bump the outbox version, add a pure
  migration, and test it against a legacy file. A newer version is never
  rewritten.
- `downloads.json`: versioned queue (§6.1), with the same "older decodes,
  newer never rewritten" rule.
- No Room schema change is expected; if a cached payload shape changes
  (`MessageMedia.isViewOnce`), bump `CacheDatabaseVersion` with an explicit
  migration and a vN→vN+1 test.
- No new `AppSettings` fields (compression is per send).

---

## 11. Testing

Pure logic (common `shared/test`):

- crop/rotation math, normalized layer transforms after crop/rotate.
- filter matrices on known pixels; stack blur and pixelate on a 4×4 fixture.
- undo/redo of `EditorDocument`.
- JPEG/PNG/WebP EXIF sanitizer against fixtures with GPS, orientation, ICC.
- trim range math and frame-timestamp → progress mapping (codec-free part).
- download queue: concurrency, priority preemption, persistence migration,
  "already cached" reconciliation.
- outbox: paused entries survive a restart, view-once migration, per-file
  failure isolation.
- `fileTypeFamily` table.

Provider tests: `AccountSessionContract` gains checks for the defaults (all new
capabilities false; methods return failure/success without throwing). The
Telegram contract test asserts `supportsViewOnce` and
`supportsChatWallpaper` match what the pinned TDLib can do.

Manual checks: Android emulator for editor gestures, background upload
notification, lock-screen controls, progressive seek; desktop for drag-and-drop
and FFmpeg trim.

Checks before each phase is called done: `./kotlin test -m shared` and
`./kotlin build -m desktopApp`, plus `./kotlin build -m androidApp` when an
Android source set or the manifest changed.

---

## 12. Invariants to preserve

- All sends, edits and retryable deletions go through `OutboxManager`; queued
  rows keep negative local ids; delivery stays at-least-once with no extra
  retry loop.
- Attachment review stays blocking: only Send and Discard, no back/outside
  dismiss, and the composer underneath is unusable.
- `MessageTextFormat` stays the only rich-text model; caption editing goes
  through `editMessageCaption`.
- Media is loaded through `AccountSession.loadMediaBytes(fileId, uniqueId)` /
  the new stream API — never a bare file id from a cached message.
- Every destructive action uses `ConfirmDeleteDialog` (red confirm, Cancel
  first): cancelling an upload, deleting editor/trim temp files, clearing
  cached downloads.
- Files stay under 2000 lines; split by concern, keep helpers `internal`.
- New dependencies are pinned in `libs.versions.toml` and referenced from
  `module.yaml`; no Gradle files.
- Diagnostics stay on device; never log file contents, tokens or locations.

---

## 13. Risks and things to verify first

1. **TDLib upload pause**: confirm `cancelUploadFile` exists in
   tdl-coroutines 18.0.0. If not, pause degrades to "cancel and retry", and the
   UI copy must say so.
2. **View-once in cloud chats**: confirm TDLib accepts
   `MessageSelfDestructTypeImmediately` for a normal chat. If not, view-once
   becomes display-only and the row toggle is removed.
3. **Wallpaper setters**: confirm the exact TDLib methods for chat themes and
   backgrounds; the per-chat change is visible to the peer.
4. **media3 size**: adds ~1–2 MB to the Android APK; acceptable, but it
   replaces the current `MediaPlayer` path, so the video player needs a full
   regression pass (audio track, aspect ratio, quality switching).
5. **Exact trim on Android** is a real transcode: 60 s of 1080p takes seconds
   to tens of seconds. Progress UI and cancel are mandatory.
6. **Blur memory**: a 4096 px image is ~64 MB as `IntArray`; bake runs once, off
   the main thread, and the editor never holds two full buffers.
