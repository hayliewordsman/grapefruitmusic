#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later
#
# Prepares a Grapefruitgram Messenger source tree: Telegram X at the commit pinned in
# upstream.properties, with Grapefruitgram's patches, new files and deletions applied, and a
# local.properties for its build.
#
#   messenger/prepare.sh [options] <work-dir>
#
# Options:
#   --tdlib prebuilt   Use the TDLib/OpenSSL libraries Telegram X ships (Git LFS). Default.
#   --tdlib source     Rebuild TDLib and OpenSSL from source (tdlib/source/rebuild.sh): slow, but
#                      no prebuilt native code is used (what F-Droid needs).
#   --tdlib none       Leave native libraries alone (compile checks of Java/Kotlin only).
#   --sample-api-id    Build with Telegram X's sample API credentials, marked experimental.
#                      For compile checks only: never publish such a build.
#   --no-fetch         <work-dir> already holds the pinned checkout (e.g. an F-Droid srclib).
#
# Environment:
#   TELEGRAM_API_ID, TELEGRAM_API_HASH   Grapefruitgram's own credentials from my.telegram.org
#                                        (required unless --sample-api-id).
#   ANDROID_HOME (or ANDROID_SDK_ROOT)   Android SDK with NDK 23.2.8568313 and CMake 3.22.1.
#   RUSTUP_HOME                          rustup home with Rust of version.rust (default: rustup show home).
#   GRAPEFRUIT_KEYSTORE, GRAPEFRUIT_KEYSTORE_PASSWORD
#                                        Optional: sign with the permanent Grapefruit key (alias
#                                        "grapefruit"), as the other Grapefruit apps are.
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO="$(cd "$HERE/.." && pwd)"
TDLIB_MODE=prebuilt
SAMPLE_API=0
FETCH=1
WORK=""

while [ $# -gt 0 ]; do
  case "$1" in
    --tdlib) TDLIB_MODE="$2"; shift 2 ;;
    --sample-api-id) SAMPLE_API=1; shift ;;
    --no-fetch) FETCH=0; shift ;;
    -h|--help) sed -n '3,27p' "$0"; exit 0 ;;
    -*) echo "Unknown option: $1" >&2; exit 2 ;;
    *) WORK="$1"; shift ;;
  esac
done
[ -n "$WORK" ] || { echo "Usage: $0 [options] <work-dir>" >&2; exit 2; }
case "$TDLIB_MODE" in prebuilt|source|none) ;; *) echo "--tdlib must be prebuilt, source or none" >&2; exit 2 ;; esac

prop() { sed -n "s/^$1=//p" "$HERE/upstream.properties"; }
UPSTREAM_URL="$(prop url)"
UPSTREAM_COMMIT="$(prop commit)"

# 1. Telegram X at the pinned commit, with its submodules.
if [ "$FETCH" = 1 ]; then
  if [ ! -d "$WORK/.git" ]; then
    mkdir -p "$WORK"
    git -C "$WORK" init -q
    git -C "$WORK" remote add origin "$UPSTREAM_URL"
  fi
  git -C "$WORK" fetch -q --depth 1 origin "$UPSTREAM_COMMIT"
  git -C "$WORK" checkout -q --force "$UPSTREAM_COMMIT"
  git -C "$WORK" submodule -q update --init --recursive --depth 1 --jobs 6
fi
WORK="$(cd "$WORK" && pwd)"
actual="$(git -C "$WORK" rev-parse HEAD)"
if [ "$actual" != "$UPSTREAM_COMMIT" ]; then
  echo "$WORK is at $actual, not the pinned $UPSTREAM_COMMIT" >&2
  exit 1
fi
if [ -n "$(git -C "$WORK" status --porcelain --ignore-submodules=all -- . ':!local.properties')" ]; then
  echo "$WORK has changes already; prepare a fresh checkout (or: git -C $WORK checkout -f . && git -C $WORK clean -fd)" >&2
  exit 1
fi

# 2. Deletions, patches, new files.
while IFS= read -r path; do
  case "$path" in ''|'#'*) continue ;; esac
  git -C "$WORK" rm -r -q --cached -- "$path"
  rm -rf "${WORK:?}/$path"
done < "$HERE/removed-paths.txt"
for patch in "$HERE"/patches/*.patch; do
  echo "Applying $(basename "$patch")"
  git -C "$WORK" apply --whitespace=nowarn "$patch"
done
cp -R "$HERE/overlay/." "$WORK/"
# Geist (SIL OFL 1.1), shared with the other Grapefruit apps.
for weight in Regular Medium SemiBold; do
  cp "$REPO/grapefruit-ui/src/main/assets/fonts/Geist-$weight.ttf" "$WORK/app/src/main/assets/fonts/"
done
cp "$REPO/grapefruit-ui/src/main/assets/fonts/Geist-OFL.txt" "$WORK/app/src/main/assets/fonts/"

# 3. Native libraries.
if [ "$TDLIB_MODE" = prebuilt ]; then
  git -C "$WORK/tdlib" lfs install --local > /dev/null
  git -C "$WORK/tdlib" lfs pull
elif [ "$TDLIB_MODE" = source ]; then
  echo "Rebuilding OpenSSL and TDLib from source (this takes a long time)..."
  (cd "$WORK/tdlib/source" && ./rebuild.sh "$WORK/build/tdlib-symbols")
fi

# 4. local.properties
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
[ -n "$SDK" ] || { echo "Set ANDROID_HOME to the Android SDK" >&2; exit 1; }
RUST_HOME="${RUSTUP_HOME:-$(rustup show home 2>/dev/null || true)}"
[ -n "$RUST_HOME" ] || { echo "rustup not found: install it and run scripts/setup-rust.sh in $WORK" >&2; exit 1; }
{
  echo "# Written by messenger/prepare.sh"
  echo "sdk.dir=$SDK"
  echo "rust.dir=$RUST_HOME"
  echo "app.id=io.github.hayliewordsman.grapefruitgram"
  echo "app.name=Grapefruitgram Messenger"
  echo "app.download_url=https://hayliewordsman.github.io/grapefruitmusic/"
  echo "app.sources_url=https://github.com/hayliewordsman/grapefruitmusic/tree/HEAD/messenger"
  echo "tgx.extension=unifiedpush"
  if [ "$SAMPLE_API" = 1 ]; then
    echo "# Telegram X's sample API credentials: test builds only."
    echo "app.experimental=true"
  else
    [ -n "${TELEGRAM_API_ID:-}" ] && [ -n "${TELEGRAM_API_HASH:-}" ] || {
      echo "Set TELEGRAM_API_ID and TELEGRAM_API_HASH (https://my.telegram.org/apps), or pass --sample-api-id for a test build" >&2
      exit 1
    }
    echo "telegram.api_id=$TELEGRAM_API_ID"
    echo "telegram.api_hash=$TELEGRAM_API_HASH"
  fi
  if [ -n "${GRAPEFRUIT_KEYSTORE:-}" ]; then
    echo "keystore.file=$WORK/keystore.properties"
  fi
} > "$WORK/local.properties"
if [ -n "${GRAPEFRUIT_KEYSTORE:-}" ]; then
  umask 077
  {
    echo "keystore.file=$GRAPEFRUIT_KEYSTORE"
    echo "keystore.password=${GRAPEFRUIT_KEYSTORE_PASSWORD:?GRAPEFRUIT_KEYSTORE_PASSWORD is required with GRAPEFRUIT_KEYSTORE}"
    echo "key.alias=grapefruit"
    echo "key.password=$GRAPEFRUIT_KEYSTORE_PASSWORD"
  } > "$WORK/keystore.properties"
fi

echo "Grapefruitgram Messenger source is ready in $WORK"
echo "Build for Android 4.2.2 and later:"
echo "  (cd $WORK && ./gradlew :app:assembleLegacyRelease -PuseLegacyNdk=true -Pabi=arm32)"
