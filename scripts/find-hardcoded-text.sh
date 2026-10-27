#!/usr/bin/env bash
# Lists string literals in UI-facing Kotlin that look like user-visible text. Developer messages
# (require/check/error), log tags, keys, routes and SQL are skipped. Usage: scripts/find-hardcoded-text.sh [path…]
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
base="$root/app/src/main/java/com/ironquest/app"
paths=("$@")
[ ${#paths[@]} -eq 0 ] && paths=("$base/ui" "$base/notifications" "$base/domain" "$base/data")
grep -rnE '"[^"]*[A-Za-z]{2,}[^"]*"' --include='*.kt' "${paths[@]}" \
  | grep -vE '^\S+:\s*(import|package) ' \
  | grep -vE '(require|check|error|requireNotNull|checkNotNull|IllegalStateException|IllegalArgumentException)\(' \
  | grep -vE 'Log\.[dewiv]\(|TAG\b|const val [A-Z_]+ = "[a-z0-9_.:/{}-]*"' \
  | grep -vE '"(SELECT|INSERT|UPDATE|DELETE|CREATE|ALTER|PRAGMA|DROP) ' \
  | grep -vE '@(Query|Entity|ColumnInfo|SerializedName|Suppress|OptIn)\(' \
  | grep -vE '"[a-z0-9_]+(/[a-z0-9_{}]+)*"' \
  | grep -vE '//\s*not UI text' \
  | while IFS= read -r hit; do
      # A marker may also sit on its own line just above the literal (formatters keep it there).
      file="${hit%%:*}"; rest="${hit#*:}"; line="${rest%%:*}"
      if [ "$line" -gt 1 ] && sed -n "$((line - 1))p" "$file" | grep -qE '^\s*//\s*not UI text'; then continue; fi
      printf '%s\n' "$hit"
    done \
  || true
