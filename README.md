# Grapefruit AI

A native Android app (Android 4.2.2 and later) that is a general-purpose AI assistant on
[OpenRouter](https://openrouter.ai) and a remote frontend for an [OpenCode](https://opencode.ai)
coding agent running on your own server.

- **Application ID:** `io.github.hayliewordsman.grapefruitai`
- **Minimum Android:** 4.2.2 (API 17), tested on the API 17 framework with Robolectric. Target API 34.
- **Branch:** `grapefruit-ai-android-4.2.2` (independent of the Grapefruit OS branches)
- **Look:** the four official Grapefruit themes (Grapefruit Dark, Grapefruit Light, Pitch Black,
  Victory Blue), Geist type, pill controls, grouped settings rows, the floating pill tab bar (a rail
  in landscape), and the Geist Pixel Triangle wordmark on the About section.

## Three modes

| Tab | What it does |
| --- | --- |
| **Chat** (Chat / Research) | Streams replies from the OpenRouter model you pick, renders Markdown with copyable code blocks, takes image/PDF/text attachments when the model accepts them, and keeps conversation history on the device. Web search is a per-app toggle that uses OpenRouter's documented `web` plugin; with it off, the model is told it has no live web access. |
| **Images** | Generates images with OpenRouter's Image API (`POST /api/v1/images`), or through chat completions for image models only served there. Aspect ratio, resolution and reference images (for editing) appear only when that model lists them in `GET /api/v1/images/models`. Results can be saved as PNG or JPEG. |
| **Code** (OpenCode Agent) | Connects to `opencode serve` over HTTPS with basic auth: create and resume sessions, choose the server's models (OpenRouter-backed ones first) and agent, send prompts, watch tool calls live, approve or reject permission requests, browse project files and changes, and download files and build artifacts (APKs). Shows token use and cost the server reports. |

The model picker lists OpenRouter's whole live catalog (`GET /api/v1/models?output_modalities=all`)
with search, Chat/Coding/Image filters, capability filters (tools, vision, files, reasoning, free)
and sorting by name, provider, input price, output price, context window and age. Each model shows
its provider, context window, input/output modalities, tool calling and prices in **US dollars**,
each in its real unit:

- **Text models:** per million input and output tokens, plus per-request and web-search charges
  where OpenRouter lists them.
- **Image models:** the generation price from OpenRouter's Images API
  (`GET /api/v1/images/models/{id}/endpoints`): per image (`Image generation: $0.01/image`), per
  megapixel (`$0.03/megapixel`) or per million image tokens, with resolution and quality variants,
  input/reference image prices and per-request surcharges. Text token prices are only shown for
  image models that produce text or actually charge for prompt text, so image-only models don't show
  a misleading "Input: Free / Output: Free".
- If the Images API lists no prices for a model (on 2026-11-13 that was only `meta/muse-image`),
  the app reads the price OpenRouter publishes in that model's openrouter.ai page (its schema.org
  price data) and says so. If neither has one, it shows OpenRouter's listed image token rate, or
  "not listed".

A price OpenRouter doesn't list is never estimated. Touch and hold a model for every listed price.
Image prices are fetched in the background after the model list (one small request per image model)
and cached with it for six hours.

## Install on Android

1. Open the repository's **Actions** tab, choose the latest successful **Grapefruit AI debug APK**
   run on `grapefruit-ai-android-4.2.2`, and download the `grapefruit-ai-debug-apk` artifact
   (a zip; artifacts are kept for 14 days).
2. Unzip it to get `app-debug.apk` and copy it to the tablet.
3. Allow the install:
   - Android 4.2–7: *Settings → Security → Unknown sources*.
   - Android 8+: allow your file manager or browser to *install unknown apps* when asked.
4. Open the APK to install it, or use `adb install app-debug.apk`.

The debug APK is signed with a debug key. A release build (manual workflow, signed with your own
key) has a different signature, so uninstall the debug build before installing it.

## OpenRouter setup

1. Create an API key at <https://openrouter.ai/keys>. Setting a credit limit on the key is a good
   idea.
2. In the app: **Settings → OpenRouter → API key**, paste it, Save.
3. Optional: **Check key and credit** shows the key's usage and limit (`GET /api/v1/key`).

Where the key is kept (**Settings → Key storage**):

- **This session only** (default): in memory, never written to storage. Re-enter it after Android
  closes the app.
- **Saved, Android Keystore** (Android 4.3+): encrypted (AES-256 + HMAC-SHA256) with keys wrapped
  by the Android Keystore. Copying the app's files off the device isn't enough to read it; someone
  with root on the device can.
- **Saved, passphrase** (all versions, including 4.2.2, which has no app-usable Keystore):
  encrypted with keys from your passphrase (PBKDF2-HMAC-SHA1, 60,000 rounds); you unlock it after
  each launch. It is only as strong as the passphrase.

The key is sent only to `openrouter.ai`, never logged, never built into the APK, never backed up
(backups are disabled) and **never sent to the OpenCode server**.

## OpenCode backend

The Code tab drives an OpenCode server you run yourself (for example on a VPS). Nothing about the
Android build runs on that server: APKs are built on GitHub-hosted runners.

### Server

```bash
# On the server: a password is required, and the server listens on localhost only.
export OPENCODE_SERVER_PASSWORD='a long random password'
cd /path/to/your/project
opencode serve --hostname 127.0.0.1 --port 4096
```

Put an HTTPS reverse proxy in front of it; the app refuses plain HTTP. With Caddy, for example:

```
agent.example.com {
    reverse_proxy 127.0.0.1:4096
}
```

Never expose the OpenCode port to the internet without the password and TLS. Configure the provider
(for example OpenRouter) **on the server**, e.g. with `opencode auth login`.

Recommended `opencode.json` permissions, so the app asks you before the agent edits files or runs
commands:

```json
{
  "$schema": "https://opencode.ai/config.json",
  "permission": {
    "edit": "ask",
    "bash": "ask",
    "webfetch": "ask",
    "websearch": "ask",
    "external_directory": "ask"
  }
}
```

### App

**Settings → OpenCode backend**: Server URL (`https://…`), Username (default `opencode`),
Password (stored like the API key), Project (the server's projects, or a path; sent as OpenCode's
`directory` parameter), and Protected projects. Then **Test connection**.

In the Code tab, permission requests appear above the composer with **Reject**, **Allow once**
and (for routine requests only) **Always**. The app adds its own second confirmation for anything
that looks destructive or publishing — `git push`, force operations, `rm`, `git reset --hard`,
history rewrites, `sudo`, piping downloads into a shell, package publishing, GitHub releases,
merges and workflow runs — and for anything touching a protected project. "Always" is never offered
for those.

### Billing and keys (important)

OpenCode's server API has no per-request API key. Agent runs therefore use the provider credentials
configured **on the server** and are billed to that account. Grapefruit AI does not send your
OpenRouter key to the server, does not change server secrets, and says so in the Code tab's status
sheet. If you want agent runs on your own OpenRouter account, configure your key on your server.

### Web research

- **Chat:** OpenRouter's `web` plugin, when you turn Web search on (charged per search by
  OpenRouter; about $0.007 per request with its default Exa engine at the time of writing; native
  search passes the provider's price through). Results come back as citations under the reply.
- **Code:** the agent uses whatever web tools the OpenCode server has (its `webfetch`/`websearch`
  tools and any search MCP servers you configure). The app shows those tool calls; it adds none.

## Security

- HTTPS only, with certificate and host-name validation always on. On Android 4.2.2 the shared
  Grapefruit TLS helper installs Conscrypt (TLS 1.2/1.3) and adds a few bundled root certificates
  the old system lacks (ISRG Root X1 and others); it never accepts a chain neither the system nor the
  bundle trusts. URLs with embedded credentials are refused.
- Android 7+: a network security config forbids cleartext and user-added CAs.
- No analytics or crash reporting. Conversations, attachments and generated images stay in the
  app's private storage and are excluded from backups.
- Saved images go to `Pictures/Grapefruit AI`, downloads to `Download/Grapefruit AI` (MediaStore on
  Android 10+; the storage permission is requested only on Android 6–9 when you save).

## Building

All builds run on GitHub-hosted runners (`.github/workflows/grapefruit-ai.yml`): on every push to
`grapefruit-ai-android-4.2.2` and on manual dispatch, the workflow runs the unit tests (including the
Robolectric tests on the Android 4.2 framework), lint (including `NewApi` checks against minSdk 17),
builds the debug APK and uploads it as an artifact (14-day retention; reports 7 days).

`grapefruit-ai-release.yml` is a separate, manual-only workflow for release APKs. It signs only if
the repository secrets `GRAPEFRUIT_AI_KEYSTORE_BASE64` and `GRAPEFRUIT_AI_KEYSTORE_PASSWORD` exist
(optionally `GRAPEFRUIT_AI_KEY_ALIAS`); the key is written to the runner's temp directory and deleted
afterwards. Debug builds never use it.

Local builds are possible with JDK 17+ and an Android SDK (`./gradlew :app:assembleDebug`), but
nothing in this project requires the Android SDK on a server.

## Status

See [docs/STATUS.md](docs/STATUS.md) for what has been tested, how, and what remains incomplete.

## Credits

Geist and Geist Pixel by Vercel (SIL Open Font License 1.1, `app/src/main/assets/fonts/Geist-OFL.txt`).
The wordmark font is a subset of Geist Pixel Triangle containing only the letters of "Grapefruit AI".
Conscrypt (Apache License 2.0). Not affiliated with OpenRouter or OpenCode.
