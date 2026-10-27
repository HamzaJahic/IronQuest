#!/usr/bin/env bash
# Zips the project before a refactor phase so any step can be rolled back by hand.
# Usage: scripts/snapshot.sh <label>   → ../IronQuest-snapshots/<date>-<label>.zip
set -euo pipefail
label="${1:?usage: scripts/snapshot.sh <label>}"
root="$(cd "$(dirname "$0")/.." && pwd)"
destination="$(dirname "$root")/IronQuest-snapshots"
mkdir -p "$destination"
archive="$destination/$(date +%Y-%m-%d-%H%M)-$label.zip"
cd "$root"
zip -qr "$archive" . -x 'build/*' '*/build/*' '.gradle/*' '.kotlin/*' '.idea/*'
echo "$archive ($(du -h "$archive" | cut -f1))"
