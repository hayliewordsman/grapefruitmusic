#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later
#
# Rewrites messenger/patches from a Telegram X working tree: one patch per commit made on top of
# the pinned upstream commit, numbered and named after the commit's subject. Files that come from
# messenger/overlay, the Geist fonts (copied from grapefruit-ui) and messenger/removed-paths.txt are
# left out of the patches, since prepare.sh supplies or deletes them.
#
#   messenger/tools/export-patches.sh <telegram-x-tree>
#
# Then check the result reproduces the tree:  messenger/prepare.sh --no-fetch --tdlib none ...
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TREE="${1:?Usage: $0 <telegram-x-tree>}"
BASE="$(sed -n 's/^commit=//p' "$HERE/upstream.properties")"

excludes=(":(exclude,glob)app/src/main/assets/fonts/Geist-*")
while IFS= read -r path; do
  excludes+=(":(exclude,literal)$path")
done < <(cd "$HERE/overlay" && find . -type f | sed 's|^\./||')
while IFS= read -r path; do
  case "$path" in ''|'#'*) continue ;; esac
  excludes+=(":(exclude)$path")
done < "$HERE/removed-paths.txt"

rm -f "$HERE"/patches/*.patch
n=1
prev="$BASE"
while IFS= read -r commit; do
  subject="$(git -C "$TREE" log -1 --format=%s "$commit")"
  slug="$(printf '%s' "$subject" | tr 'A-Z' 'a-z' | sed -E 's/[^a-z0-9]+/-/g; s/^-+|-+$//g' | cut -c1-48)"
  out="$HERE/patches/$(printf '%02d' "$n")-$slug.patch"
  git -C "$TREE" diff --no-color --no-ext-diff "$prev" "$commit" -- . "${excludes[@]}" > "$out"
  if [ -s "$out" ]; then
    echo "$(basename "$out")"
    n=$((n + 1))
  else
    rm "$out"
  fi
  prev="$commit"
done < <(git -C "$TREE" rev-list --reverse "$BASE..HEAD")
