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
| Image prices in real units (per image, per megapixel, per M image tokens, per request; resolution variants; several providers), the model-page fallback for models the Images API doesn't price (Meta Muse Image: `$0.01/image`), and no "Input: Free / Output: Free" for image-only models | `ImagePricingTest`, against live Images API responses and the openrouter.ai/meta/muse-image page (2026-11-13); also checked by hand against all 66 live Images API models |
| Streaming chunk handling: text, reasoning, `url_citation` annotations, usage/cost, mid-stream errors | `ChatChunkTest`, `SseTest` (OpenRouter's documented SSE format) |
| Markdown: headings, lists, quotes, rules, tables, fenced code (also unclosed while streaming); code blocks have Copy | `MarkdownTest`, `Api17Test` |
| OpenCode V2 client: every request the app makes (info, location, models, default model, providers, agents, projects, sessions list/create/get/delete, switch model/agent, prompt, interrupt, messages, active, diff, permissions and reply, fs list/read/find, vcs status, events) is a documented V2 route with only documented query parameters and body fields, over HTTPS with Basic auth | `OpenCodeApiTest` (Robolectric, API 17), against the routes and request schemas of https://opencode.ai/docs/openapi.json (fetched 2026-11-13, kept as `opencode-v2-routes.json`) |
| V2 event handling: `session.text/reasoning.*` by ordinal, `session.tool.*`, `session.step.*`, `session.execution.*`, retries, usage totals, `permission.asked/replied`, other sessions ignored; loading projected messages | `TranscriptTest`, with event and message shapes from the same OpenAPI document |
| Connection test outcomes: missing settings, HTTP 401, HTTP 404 (API vs project directory), TLS (untrusted, wrong name), network (DNS, refused, timeout), non-OpenCode answers | `ConnectionCheckTest`, `OpenCodeApiTest` |
| Risk classification for permission requests (git push, rm, reset --hard, sudo, publishing, protected projects…) | `RiskTest` |

## Implemented but not exercised against live services

These follow the current official documentation (OpenRouter docs, OpenCode server docs and the
OpenCode SDK's generated OpenAPI types) but were **not** run end to end, because no OpenRouter key
or OpenCode server was available while building:

- Real OpenRouter chat streaming, cancellation, retry/reconnect, attachments (images, PDFs, text
  files), web search plugin, image generation (Image API and chat image output) and image editing
  with reference images.
- `GET /api/v1/key` (key check).
- The Code tab against a real OpenCode 2.2.7 server: requests are checked against the published
  OpenAPI document, but no live server was available here, and the document doesn't describe the
  authentication scheme (the app keeps HTTP Basic, as OpenCode's server docs describe for
  `OPENCODE_SERVER_PASSWORD`). The meaning of `ordinal` in text/reasoning events is inferred
  (position in the message's content); the app reloads the session when a turn ends, so the final
  text always comes from the server's projection.
- Android Keystore storage (Android 4.3+): Robolectric can't emulate the keystore.
- Saving to Pictures/Download (MediaStore on Android 10+, public folders and the runtime permission
  on 6–9), and the system file picker.
- Real-device rendering on an Android 4.2.2 tablet, and TLS with Conscrypt on real 4.2.2 hardware
  (the helper is the one the other Grapefruit apps use on 4.2.2).

## Known limitations

- The model-page price fallback reads OpenRouter's public web page, which has no documented format;
  if the page changes, those models fall back to their listed token rate or "not listed".

- **No per-user key for OpenCode agents.** OpenCode's server API takes no API key per request, so
  agent runs are billed to the provider credentials on the server. The app tells you this and never
  sends your OpenRouter key to the server.
- File attachments are sent inline (data URLs), up to 12 MB each; large photos are scaled to 2048 px.
- Image generation is not streamed (partial previews from the Image API are not shown).
- Text files attached in Chat are sent as text; other files need a model with `file` input.
- The Code tab sends text prompts; attaching files to OpenCode prompts is not implemented.
- The V2 event stream (`GET /api/event`) is volatile: events during a disconnection are lost, so
  the app reloads the session after reconnecting.
- GitHub operations (Actions logs, artifact downloads, commits, pushes) happen through the agent's
  tools on the server (for example the `gh` CLI if installed there); the app shows them and asks
  for approval but has no GitHub API client of its own.
- Conversation history is stored unencrypted in the app's private storage (protected by Android's
  app sandbox only).
- English only.
