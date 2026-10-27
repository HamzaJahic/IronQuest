#!/usr/bin/env bash
# Builds app/src/main/res/font/ from the full fonts in design/fonts/: Latin + Latin Extended plus the
# punctuation and symbols the UI uses. Other scripts fall back to the system font per character.
# Re-run (and extend UNICODES) when a translation needs letters outside these ranges.
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
venv="$root/build/fonttools-venv"
if [ ! -x "$venv/bin/pyftsubset" ]; then
  python3 -m venv "$venv"
  "$venv/bin/pip" install --quiet fonttools
fi
# Basic Latin … Latin Extended-B, IPA ə, spacing modifiers, combining marks, Latin Extended Additional,
# general punctuation (incl. narrow no-break space), currency, letterlike, arrows, math (− ≥ ≈ ⋯),
# geometric shapes, dingbats (✓ ✕), BOM, replacement character.
UNICODES="U+0000-024F,U+0259,U+02B0-02FF,U+0300-036F,U+1E00-1EFF,U+2000-206F,U+20A0-20CF,U+2100-214F,U+2190-21FF,U+2200-22FF,U+25A0-25FF,U+2700-27BF,U+FEFF,U+FFFD"
# Fonts whose OFL licence declares a Reserved Font Name: a subset is a modified version and may not keep the
# name, so they ship unchanged (Lora: "with Reserved Font Name \"Lora\"").
KEEP_AS_IS=(lora_variable.ttf)
mkdir -p "$root/app/src/main/res/font"
for source in "$root"/design/fonts/*.ttf; do
  target="$root/app/src/main/res/font/$(basename "$source")"
  if [[ " ${KEEP_AS_IS[*]} " == *" $(basename "$source") "* ]]; then
    cp "$source" "$target"
    printf '%-24s %8d   (unchanged: reserved font name)\n' "$(basename "$source")" "$(stat -f%z "$source")"
    continue
  fi
  "$venv/bin/pyftsubset" "$source" \
    --unicodes="$UNICODES" \
    --layout-features='*' \
    --name-IDs='*' --name-languages='*' --name-legacy \
    --no-hinting \
    --drop-tables+=DSIG \
    --output-file="$target"
  printf '%-24s %8d → %8d bytes\n' "$(basename "$source")" "$(stat -f%z "$source")" "$(stat -f%z "$target")"
done
"$venv/bin/python" "$root/scripts/check-font-coverage.py"
