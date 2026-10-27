#!/usr/bin/env bash
# Builds the profiling APK (R8, release-like) and prints the numbers for the refactor spec's appendix.
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
cd "$root"
./gradlew -q :app:assembleProfiling
apk=app/build/outputs/apk/profiling/app-profiling.apk
analyzer="${ANDROID_HOME:-$HOME/Library/Android/sdk}/cmdline-tools/latest/bin/apkanalyzer"
echo "APK bytes:            $(stat -f%z "$apk")"
echo "Download bytes:       $("$analyzer" apk download-size "$apk")"
echo "DEX bytes:            $(unzip -l "$apk" 'classes*.dex' | tail -1 | awk '{print $1}')"
echo "DEX method refs:      $("$analyzer" dex references "$apk" | awk '{sum += $2} END {print sum}')"
echo "String locales:       $("$analyzer" resources configs --type string "$apk" | wc -l | tr -d ' ')"
echo "res/ entries:         $(unzip -l "$apk" 'res/*' | tail -1 | awk '{print $2}')"
echo "Kotlin lines (main):  $(find app/src/main -name '*.kt' -print0 | xargs -0 cat | wc -l | tr -d ' ')"
echo "@Composable (main):   $(grep -r '@Composable' --include='*.kt' app/src/main | wc -l | tr -d ' ')"
