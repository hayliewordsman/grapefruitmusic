# Grapefruit AI — status (1.0 beta 1)

What is verified, how, and what isn't. "Tested" below means an automated test or a check
described here; nothing else is claimed to work.

## Verified

| Area | How |
| --- | --- |
| Builds for minSdk 17 / target 34, single dex, Conscrypt native libraries for armeabi-v7a, arm64, x86, x86_64 | `assembleDebug`, `aapt2 dump badging` (local and in CI) |
| No API above 17 used unguarded | Android lint `NewApi` (lint fails the build on errors) |
| App starts on the Android 4.2 (API 17) framework; all four tabs build in all four themes; landscape uses the side rail | Robolectric `@Config(sdk = 17)` tests (`Api17Test`) |
| Session-only key storage by default; passphrase storage encrypts, locks after restart, unlocks, and clears on API 17 | `Api17Test` |
| AES-256-CBC + HMAC-SHA256 round trip, tamper and wrong-key detection, PBKDF2 | `CryptoTest` |
| HTTPS-only URL policy, refusal of plain HTTP and URL credentials, readable HTTP errors | `HttpTest`, `Api17Test` |
| Model catalog parsing, prices in USD per M tokens, "not listed"/"variable" (never invented), Chat/Coding/Image filters, search, sorting | `OrModelTest`, `CatalogTest`, `FormatTest`, against a sample of OpenRouter's **live** `/api/v1/models?output_modalities=all` and `/api/v1/images/models` responses (fetched 2026-11-12) |
| Streaming chunk handling: text, reasoning, `url_citation` annotations, usage/cost, mid-stream errors | `ChatChunkTest`, `SseTest` (OpenRouter's documented SSE format) |
| Markdown: headings, lists, quotes, rules, tables, fenced code (also unclosed while streaming); code blocks have Copy | `MarkdownTest`, `Api17Test` |
| OpenCode event handling: message/part updates and deltas, session status, permissions asked/replied, other sessions ignored, token and cost totals | `TranscriptTest`, with event shapes from the `@opencode-ai/sdk` 1.19.5 OpenAPI types |
| Risk classification for permission requests (git push, rm, reset --hard, sudo, publishing, protected projects…) | `RiskTest` |

## Implemented but not exercised against live services

These follow the current official documentation (OpenRouter docs, OpenCode server docs and the
OpenCode SDK's generated OpenAPI types) but were **not** run end to end, because no OpenRouter key
or OpenCode server was available while building:

- Real OpenRouter chat streaming, cancellation, retry/reconnect, attachments (images, PDFs, text
  files), web search plugin, image generation (Image API and chat image output) and image editing
  with reference images.
- `GET /api/v1/key` (key check).
- Everything in the Code tab against a running `opencode serve`: health, providers, agents,
  sessions, `prompt_async`, the `/event` stream with reconnect, permission replies
  (`POST /permission/{id}/reply`, falling back to `/session/{id}/permissions/{id}`), abort, file
  listing/reading/status, find, downloads.
- Android Keystore storage (Android 4.3+): Robolectric can't emulate the keystore.
- Saving to Pictures/Download (MediaStore on Android 10+, public folders and the runtime permission
  on 6–9), and the system file picker.
- Real-device rendering on an Android 4.2.2 tablet, and TLS with Conscrypt on real 4.2.2 hardware
  (the helper is the one the other Grapefruit apps use on 4.2.2).

## Known limitations

- **No per-user key for OpenCode agents.** OpenCode's server API takes no API key per request, so
  agent runs are billed to the provider credentials on the server. The app tells you this and never
  sends your OpenRouter key to the server.
- File attachments are sent inline (data URLs), up to 12 MB each; large photos are scaled to 2048 px.
- Image generation is not streamed (partial previews from the Image API are not shown).
- Text files attached in Chat are sent as text; other files need a model with `file` input.
- The Code tab sends text prompts; attaching files to OpenCode prompts is not implemented.
- GitHub operations (Actions logs, artifact downloads, commits, pushes) happen through the agent's
  tools on the server (for example the `gh` CLI if installed there); the app shows them and asks
  for approval but has no GitHub API client of its own.
- Conversation history is stored unencrypted in the app's private storage (protected by Android's
  app sandbox only).
- English only.
