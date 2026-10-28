# Grapefruit Music's F-Droid repository

Grapefruit Music can be installed and updated through F-Droid from its own repository:
**https://hayliewordsman.github.io/grapefruitmusic/repo**. The workflow
[`.github/workflows/fdroid-repo.yml`](../.github/workflows/fdroid-repo.yml) builds it on GitHub
Actions and deploys it to GitHub Pages.

| File | What it is |
| --- | --- |
| `config.yml` | The repository's name, description, icon, address and signing-key settings |
| `config/categories.yml` | The categories apps here can use |
| `metadata/com.grapefruitmusic.yml` | Grapefruit Music's listing: summary, description, categories, links |
| `metadata/com.grapefruitmusic/en-US/icon.png` | The listing's icon (512 × 512) |
| `metadata/com.grapefruitmusic/en-US/changelogs/<versionCode>.txt` | Each version's "What's new" (500 characters at most) |

## How a version gets to F-Droid

1. Raise `versionCode` and `versionName` in `app/build.gradle`, and write
   `metadata/com.grapefruitmusic/en-US/changelogs/<versionCode>.txt`.
2. Push to the default branch. The workflow builds the release APK, refuses it unless it is
   signed with the permanent app key (SHA-256 `85cac0ef…e795`), publishes it as GitHub release
   `v<versionName>` with the changelog as its notes, then rebuilds the F-Droid repository from
   every release's APK and deploys it.
3. F-Droid shows the update the next time it refreshes its repositories.

A versionName with alpha, beta or rc in it becomes a GitHub prerelease, which stays out of the
F-Droid repository. Editing anything in `fdroid/` (or running the workflow by hand: Actions ›
F-Droid repo › Run workflow) redeploys the repository without making a release.

## One-time setup

### 1. Let the workflow sign releases with the app key

The release APK must carry the same key as every Grapefruit Music APK so far, or it can't
install over them. Use the files from the version 1.5 hand-over (`grapefruit-music-release.p12`,
`grapefruit-music-release.p12.base64.txt` and `SIGNING-KEY-README.txt`, which has the password).

On GitHub: Settings › Secrets and variables › Actions › **New repository secret**, twice:

- `GRAPEFRUIT_KEYSTORE_BASE64`: the whole contents of `grapefruit-music-release.p12.base64.txt`
- `GRAPEFRUIT_KEYSTORE_PASSWORD`: its password

### 2. Make the repository signing key

F-Droid checks that a repository's index is signed by the key it first saw for it, so this key is
what makes the repository trustworthy. Keep it separate from the app key: it only signs the
index, and if either key ever leaked, the other would still be safe.

Make it on your own computer, never on a shared machine or in CI. With Java 17 or later:

```sh
keytool -genkeypair -v \
  -keystore keystore.p12 -storetype PKCS12 \
  -alias grapefruit-repo \
  -keyalg RSA -keysize 4096 -sigalg SHA256withRSA \
  -validity 10000 \
  -dname "CN=Grapefruit Music F-Droid repo, O=MagicFlute LLC"
```

keytool asks for a password twice: use a long random one (let your password manager make it, or
run `openssl rand -base64 32`). Typing it at the prompt keeps it out of your shell history. The
alias must stay `grapefruit-repo`, as `config.yml` says.

Write down the key's fingerprint, which is what people's F-Droid pins:

```sh
keytool -list -v -keystore keystore.p12 -alias grapefruit-repo | grep 'SHA256:'
```

The fingerprint is that line's hex without the colons.

Back up `keystore.p12` and its password in two places (for example your password manager and an
offline USB stick). If it's lost, everyone has to remove the repository and add it again. If it
leaks, someone could sign a fake index that F-Droid would trust (for example on a copy of the
repository); they still couldn't make an update install over Grapefruit Music, because Android
checks the app key on every update.

### 3. Turn on GitHub Pages

Settings › Pages › Build and deployment › Source: **GitHub Actions**. This makes the
`github-pages` environment. In Settings › Environments › `github-pages` › Deployment branches
and tags, make sure only the default branch is allowed (today that's
`claude/laughing-cori-h5xid4`; if you rename it, `main`).

### 4. Store the repository key in GitHub

Turn the keystore into one line of text:

- Linux: `base64 -w 0 keystore.p12 > keystore.p12.b64`
- macOS: `base64 -i keystore.p12 -o keystore.p12.b64`
- Windows (PowerShell):
  `[Convert]::ToBase64String([IO.File]::ReadAllBytes("keystore.p12")) | Out-File -NoNewline keystore.p12.b64`

Then Settings › Environments › `github-pages` › Environment secrets › **Add environment secret**,
twice:

- `FDROID_KEYSTORE_BASE64`: the contents of `keystore.p12.b64`
- `FDROID_KEYSTORE_PASSWORD`: the password

Or, with the GitHub CLI:

```sh
gh secret set FDROID_KEYSTORE_BASE64 --env github-pages < keystore.p12.b64
gh secret set FDROID_KEYSTORE_PASSWORD --env github-pages   # asks for the value
```

Delete `keystore.p12.b64` afterwards, and keep `keystore.p12` only in your backups. As
environment secrets, only the job that deploys to GitHub Pages from the default branch can read
them: pull requests and other branches can't. The workflow writes the key to disk only while it
signs the index, then deletes it, and the password reaches fdroidserver as an environment
variable (`{env: FDROID_KEYSTORE_PASSWORD}` in `config.yml`), never as a file.

### 5. First run

Actions › F-Droid repo › Run workflow, on the default branch. When it finishes, its summary shows
the repository link with `?fingerprint=…`. Check that fingerprint is the one you wrote down in
step 2 before you share the link.

### 6. Add the repository in F-Droid

Open the link from the summary on the device, or scan the QR code on
https://hayliewordsman.github.io/grapefruitmusic/repo/, or in F-Droid go to Settings ›
Repositories › + and paste the link (with `?fingerprint=…`).

## Good to know

- Android 4.2 and 4.3: F-Droid's own app no longer supports Android 4.x. Its forum names version
  1.11 as the last one that runs there. The repository includes the older index format
  (`index-v1`) that such versions read, but it hasn't been tried from Android 4.2 against GitHub
  Pages' HTTPS: try it on a tablet before announcing it.
- Android 14 and later refuse to install apps that target Android 5.1 (API 22), as Grapefruit
  Music does for Android 4.2, so F-Droid can't install it there.
- "What's new" reaches F-Droid apps that read `index-v1`, and every GitHub release. fdroidserver
  2.4 leaves it out of `index-v2` for repositories of prebuilt APKs.
- The app certificate's name reads "Android Debug": it is the key the very first versions were
  signed with, kept so updates install over them. Android and F-Droid check its fingerprint, not
  its name.
- No licence is listed, because the repository has no LICENSE file yet. Add one, then add
  `License:` with its SPDX identifier to `metadata/com.grapefruitmusic.yml`.
