# MAX provider

This module implements the MAX messenger (`ru.oneme.app`, VK/OneMe) for
Scrollix. It was built by reverse-engineering `Max.apkm` and the web client
bundle served by `web.max.ru`.

## Protocol

MAX has two transports with the same opcode model and the same packet layout:

| Transport | Endpoint | Notes |
| --- | --- | --- |
| WebSocket (used here) | `wss://api.oneme.ru/websocket` | Accepts `deviceType: WEB` only |
| TLS | `api.oneme.ru:443` / `api2.oneme.ru:443` | Used by mobile apps; requires a native anti-abuse token |

Frames are binary, not JSON. The 10-byte header is:

| Byte | Field |
| --- | --- |
| 0 | protocol version (10) |
| 1 | `cmd` |
| 2-3 | `seq` (int16, big endian) |
| 4-5 | `opcode` (int16) |
| 6 | LZ4 compression factor (`0` = uncompressed) |
| 7-9 | payload length (24 bit) |

The payload is MessagePack. A non-zero compression factor means the payload is
an LZ4 block and decompresses to `length * factor` bytes. The reference client
compresses outgoing payloads of 32 bytes or more, but a factor of `0` is
accepted, so requests are sent uncompressed.

`cmd=0` is a request from the client, `cmd=1` a successful response matched by
`seq`, `cmd=3` an error (the payload carries `error`, `message`,
`localizedMessage`), and server `cmd=0` frames are pushes. Opcode 1 is a
heartbeat; the client answers a server ping with
`{"interactive": true}`. The web client wraps large integers in MessagePack
extension type 1; the codec decodes it as a number.

The WebSocket endpoint rejects `deviceType: ANDROID` with
`session init forbidden`, so this module presents the current MAX web client
(`deviceType: WEB`, `pushDeviceType: WEBPUSH`) and its browser user agent.
Nothing sent to the server identifies Scrollix.

### Sign-in

1. `INIT` (6) with `deviceId` and the `userAgent` object.
2. `VERIFICATION_REQUEST` (17) with `{phone, type: START_AUTH|RESEND, language}`.
   The response contains `trackId`, `codeLength` and `blockingDuration`; there is
   no `token` field.
3. `CODE_ENTER` (18) with `{token: trackId, verifyCode, authTokenType: "CHECK_CODE"}`.
   Success returns `tokenAttrs.LOGIN.token`. A protected account instead answers
   with `passwordChallenge {trackId, hint?}`, which is finished with
   `PASSWORD_CHECK` (115) `{trackId, password}` and returns the same login
   token; an unknown number answers with `tokenAttrs.REGISTER.token`.
4. `LOGIN` (19) with the access token, `chatsCount`/`chatsSync` 100. The
   response carries `profile.contact`, `chats` and `users`.

MAX gates SMS delivery behind a VK not-a-robot captcha on some networks. The
client mirrors the web flow:

1. `CAPTCHA_SESSION` (224) with `{source: "auth", identifier: phone}` returns
   the widget link (`id.vk.ru/not_robot_captcha?...`). A
   `captcha.create-session-failed` error means no check is needed.
2. The link is shown in an embedded browser. The widget reports its result as
   a `postMessage` frame of the shape
   `{type: "vk-sak-sdk", handler: "VKCaptchaGetResult", params: {token}}`.
3. `AUTH_REQUEST` (17) is repeated with that `captchaToken`.

Android renders the widget in a `WebView` (`shared/src@android/CaptchaView.android.kt`);
the desktop build has no embedded browser and says so instead of showing a
check that cannot be completed.

Without a session the client creates a random UUID for `deviceId`; the token,
device id and user id are stored in
`max/<accountId>/session.json` through `PlatformStorage`.

## Implemented

- SMS sign-in, resend, the VK not-a-robot captcha step when MAX asks for it,
  the password confirmation some accounts require after the code, token
  restore, logout, automatic reconnect.
- Chat list from LOGIN and `GET_CHATS` (53), with avatars downloaded through
  `fetchUrlBytes`.
- Message history with timestamp pagination (`GET_HISTORY` 49,
  `GET_MESSAGE` 71), live new/edit/delete/read/typing/reaction pushes.
- Sending text (64) and replies (`link.type: REPLY`), editing (67), deleting
  for everyone (66), forwarding as a content copy, pinning (55), read marks
  (50), reactions (178/179), chat pin/mute operations (77), chat subscribe
  (75).
- Native MAX sticker packs: ASSETS_UPDATE (27) and ASSETS_GET (26) fill the
  picker, ASSETS_ADD (29) toggles favourites, and a pack sticker is sent by
  its server id. Lottie stickers animate in chat from their `lottieUrl`.
- Sticker import from other providers. A sticker without a MAX sticker id is
  re-uploaded as a MAX photo, and an animated TGS/WebM sticker is prerendered
  into a transparent animated GIF (`renderStickerAnimationGif` in `shared`)
  before upload. Animated photos carry `isGif`, and animations uploaded as
  video are offered as `GIF` attachments so MAX animates them in place.
- Profiles for users, groups and channels, including members and the own
  profile. Usernames and mention avatars resolve from the signed-in snapshot.
- Chat and global message search (73/60), chat search, shared-media tabs
  through `GET_MEDIA` (51), media viewing through URL-backed file ids.

## Not implemented

- File and image upload. The documented flow is `FILE_UPLOAD` (87), a
  multipart POST, `NOTIF_ATTACH` (136), then `MSG_SEND` with a `FILE` attach,
  but the module does not perform uploads yet. `sendFile`/`sendFiles` return an
  explicit error.
- QR sign-in (288/289) and registration (the `REGISTER` token flow) for
  phone numbers that do not have a MAX account yet.
- Per-chat mute state loading and server-side drafts.
- Deleting for everyone is sent as `forMe=false`, with a per-account delete as
  the fallback when the server refuses it. The documented cascade needs a
  `CHAT_ACTIVITY` (92) watermark immediately before the delete; this client
  never sends 92 (reads use CHAT_READ, 50).
- Media whose attachment has no direct HTTP URL (some photos only carry
  `photoId`/`token`); those render as a placeholder.

## Layout

- `src/network/MaxSocket` — platform WebSocket, OkHttp on Android and JVM
  (`src@android/network/`, `src@jvm/network/`).
- `src/network/MaxRpc` — opcode frames, `seq` matching, pushes, heartbeat.
- `src/network/MaxJson` — JSON accessors and phone normalization.
- `src/data/api/MaxSession` — `AccountSession` implementation and mapping.
- `src/data/api/MaxProvider`, `src/MaxIntegration` — registry wiring.
