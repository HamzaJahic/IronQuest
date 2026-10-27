#!/usr/bin/env bash
# Frame-time baseline for IronQuest. Run on the profiling build (./gradlew installProfiling, then
# adb shell cmd package compile -m speed-profile -f com.ironquest.app). Compare before/after on the
# same device only. NOTE: J4 logs one real set into the app's database.
#
# Usage: scripts/measure-smoothness.sh [J1 J2 ...]   (default: all journeys)
# Env:   ADB=/path/to/adb   SERIAL=emulator-5554
ADB="${ADB:-/opt/homebrew/bin/adb}"
SERIAL="${SERIAL:-}"
PKG=com.ironquest.app
ACT=$PKG/.MainActivity
[ -n "$SERIAL" ] && ADB="$ADB -s $SERIAL"
SLEEP=0.4

SIZE=$($ADB shell wm size | grep -oE '[0-9]+x[0-9]+' | tail -1)
W=${SIZE%x*}; H=${SIZE#*x}

# ---- UI helpers ------------------------------------------------------------------------------
dump_ui() {
  $ADB shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
  $ADB shell cat /sdcard/ui.xml > /tmp/ironquest-ui.xml 2>/dev/null
}

# find_text "Label" [mode]  -> prints "x y" of the centre of the first match, or nothing.
# Matches text or content-desc, case-insensitive, exact after trimming; if there is no exact match,
# falls back to "starts with". mode: first (default) | last (lowest on screen) | bottom (only in
# the bottom 15 % of the screen, i.e. the nav bar) | top (only in the top 25 %) | content
# (above the nav bar).
find_text() {
  python3 - "$1" "${2:-first}" "$H" /tmp/ironquest-ui.xml <<'PY'
import sys, re, xml.etree.ElementTree as ET
label, mode, H, path = sys.argv[1].strip().lower(), sys.argv[2], int(sys.argv[3]), sys.argv[4]
try:
    root = ET.parse(path).getroot()
except Exception:
    sys.exit(0)
hits = []
for n in root.iter("node"):
    b = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", n.get("bounds", ""))
    if not b: continue
    x1, y1, x2, y2 = map(int, b.groups())
    cy = (y1 + y2) // 2
    if mode == "bottom" and cy < H * 0.85: continue
    if mode == "top" and cy > H * 0.25: continue
    if mode == "content" and cy > H * 0.85: continue
    for v in (n.get("text", ""), n.get("content-desc", "")):
        v = v.strip().lower()
        if not v: continue
        if v == label: hits.append((0, y1, (x1 + x2) // 2, cy))
        elif v.startswith(label): hits.append((1, y1, (x1 + x2) // 2, cy))
if not hits: sys.exit(0)
best = min(h[0] for h in hits)
hits = [h for h in hits if h[0] == best]
hits.sort(key=lambda h: h[1])
h = hits[-1] if mode == "last" else hits[0]
print(h[2], h[3])
PY
}

# tap_text "Label" [mode] [--nodump]: dump, find, tap. Returns 1 if not found.
tap_text() {
  [ "$3" = "--nodump" ] || dump_ui
  local xy; xy=$(find_text "$1" "${2:-first}")
  if [ -z "$xy" ]; then return 1; fi
  $ADB shell input tap $xy
  sleep "$SLEEP"
}

tap_tab() { tap_text "$1" bottom; }

# first tappable-looking row below the title: any text node in the content area under y=Ymin
tap_first_row() {
  dump_ui
  local xy
  xy=$(python3 - "$H" "$1" /tmp/ironquest-ui.xml <<'PY'
import sys, re, xml.etree.ElementTree as ET
H, ymin = int(sys.argv[1]), float(sys.argv[2])
root = ET.parse(sys.argv[3]).getroot()
c = []
for n in root.iter("node"):
    b = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", n.get("bounds", ""))
    if not b: continue
    x1, y1, x2, y2 = map(int, b.groups())
    if n.get("clickable") == "true" and y1 > H * ymin and y2 < H * 0.85 and (x2 - x1) > 300:
        c.append((y1, (x1 + x2) // 2, (y1 + y2) // 2))
c.sort()
if c: print(c[0][1], c[0][2])
PY
)
  [ -z "$xy" ] && return 1
  $ADB shell input tap $xy
  sleep "$SLEEP"
}

back() { $ADB shell input keyevent KEYCODE_BACK; sleep "$SLEEP"; }
swipe_h() { # left|right
  local y=$((H / 2)) a=$((W * 85 / 100)) b=$((W * 15 / 100))
  if [ "$1" = left ]; then $ADB shell input swipe $a $y $b $y 150; else $ADB shell input swipe $b $y $a $y 150; fi
  sleep "$SLEEP"
}
swipe_v() { # up (scroll down) | down (scroll up)
  local x=$((W / 2)) a=$((H * 70 / 100)) b=$((H * 30 / 100))
  if [ "$1" = up ]; then $ADB shell input swipe $x $a $x $b 250; else $ADB shell input swipe $x $b $x $a 250; fi
  sleep "$SLEEP"
}

fresh_start() {
  $ADB shell am force-stop $PKG
  $ADB shell am start -n $ACT --ez com.ironquest.app.SKIP_ONBOARDING true >/dev/null
  sleep 2.5
}

reset_gfx() { $ADB shell dumpsys gfxinfo $PKG reset >/dev/null; }
report() { echo "== $1"; $ADB shell dumpsys gfxinfo $PKG | grep -E "Total frames|Janky frames|50th|90th|95th|99th" | head -7; }
skip() { echo "SKIP $1: $2"; }

# ---- journeys --------------------------------------------------------------------------------
J1() {
  fresh_start
  tap_tab Today || { skip J1 "bottom tabs not found"; return; }
  reset_gfx
  for i in 1 2 3; do
    for t in Log Food You Today; do tap_tab "$t"; done
  done
  report "J1 Tabs"
}

J2() {
  fresh_start
  tap_tab Food || { skip J2 "Food tab not found"; return; }
  dump_ui
  if [ -z "$(find_text Meals content)" ]; then skip J2 "'Meals' link not found on Food"; return; fi
  reset_gfx
  for i in 1 2 3; do
    tap_tab Food
    if tap_text Meals content; then
      tap_first_row 0.15 || echo "  (no meal row found; pushing/popping Meals only)"
      back; back
    fi
    tap_tab You
    # Program row lives inside the Plans section; open it only if it is not already open.
    dump_ui
    [ -z "$(find_text Program content)" ] && tap_text Plans content
    if tap_text Program content; then back; else echo "  (Program row not found)"; fi
  done
  report "J2 Push/pop"
}

J3() {
  fresh_start
  tap_tab You || { skip J3 "You tab not found"; return; }
  dump_ui
  local secs=(Plans Goals Appearance "Export & import" "Your data") found=0
  reset_gfx
  for i in 1 2; do
    for s in "${secs[@]}"; do
      if tap_text "$s" content; then
        found=1
        tap_text "$s" content   # close again (header stays in place when closing)
      fi
    done
  done
  if [ $found = 0 ]; then skip J3 "no section header found"; return; fi
  report "J3 You sections"
}

J4() {
  fresh_start
  tap_tab Today || { skip J4 "Today tab not found"; return; }
  if ! tap_text "Start workout" content && ! tap_text "Resume workout" content; then
    skip J4 "Start/Resume button not found"; return
  fi
  sleep 1
  dump_ui
  if [ -z "$(find_text "More weight")" ]; then skip J4 "workout page not showing (More weight)"; return; fi
  reset_gfx
  tap_text "More weight" first >/dev/null
  for i in $(seq 1 9); do tap_text "More weight" first --nodump; done
  tap_text "More reps"
  for i in $(seq 1 4); do tap_text "More reps" first --nodump; done
  tap_text "Log set"
  for i in 1 2 3 4 5; do swipe_h left; done
  for i in 1 2 3 4 5; do swipe_h right; done
  report "J4 Workout"
  tap_text "‹ Today" top || back
  sleep 0.5
}

J5() {
  fresh_start
  tap_tab Food || { skip J5 "Food tab not found"; return; }
  dump_ui
  if [ -z "$(find_text Week content)" ]; then skip J5 "Day/Week/Month control not found"; return; fi
  reset_gfx
  for i in 1 2 3; do
    for m in Day Week Month Day; do tap_text "$m" content; done
  done
  swipe_v up; swipe_v up; swipe_v down; swipe_v down
  report "J5 Food modes"
}

J6() {
  echo "== J6 Cold start"
  local times=() t
  for i in 1 2 3 4 5; do
    $ADB shell am force-stop $PKG
    sleep 1
    t=$($ADB shell am start -W -n $ACT --ez com.ironquest.app.SKIP_ONBOARDING true | grep TotalTime | grep -oE '[0-9]+')
    echo "  TotalTime: $t ms"
    times+=("$t")
    sleep 1.5
  done
  printf '%s\n' "${times[@]}" | sort -n | awk '{a[NR]=$1} END{print "  median TotalTime: " a[int((NR+1)/2)] " ms"}'
}

JOURNEYS=("$@"); [ ${#JOURNEYS[@]} -eq 0 ] && JOURNEYS=(J1 J2 J3 J4 J5 J6)
for j in "${JOURNEYS[@]}"; do "$j"; done
