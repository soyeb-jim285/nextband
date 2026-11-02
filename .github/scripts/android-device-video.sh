#!/usr/bin/env bash
# Records real interaction clips: `adb shell screenrecord` in the background while taps/swipes/text are sent with adb input.
# Taps find their target by dumping the UI (uiautomator) and reading the bounds of a text / content-description, no hardcoded coordinates.
# Demo mode (see apps/android/.../core/Demo.kt) opens each screen on fixtures. Output: device-video/*.mp4 plus dumps/ for debugging.
set -u
APK=apps/android/app/build/outputs/apk/release/app-release.apk
PKG=com.soyeb.ieltspractice
OUT=device-video
mkdir -p "$OUT/dumps"
adb install -r "$APK"
# Compile now like a Play install would (profileinstaller would otherwise wait for idle dexopt).
adb shell cmd package compile -m speed-profile -f "$PKG" || true
adb shell pm grant "$PKG" android.permission.RECORD_AUDIO || true
adb shell pm grant "$PKG" android.permission.POST_NOTIFICATIONS || true
adb shell settings put global hide_error_dialogs 1
adb shell settings put secure anr_show_background 0 || true
for l in com.google.android.apps.nexuslauncher com.android.launcher3; do adb shell pm disable-user --user 0 "$l" > /dev/null 2>&1 || true; done
sleep 8 # SystemUI restarts when the launcher goes; set the demo status bar after that
adb shell settings put global sysui_demo_allowed 1
adb shell am broadcast -a com.android.systemui.demo -e command enter > /dev/null
adb shell am broadcast -a com.android.systemui.demo -e command clock -e hhmm 0941 > /dev/null
adb shell am broadcast -a com.android.systemui.demo -e command battery -e level 100 -e plugged false > /dev/null
adb shell am broadcast -a com.android.systemui.demo -e command network -e wifi show -e level 4 > /dev/null
adb shell am broadcast -a com.android.systemui.demo -e command notifications -e visible false > /dev/null
adb shell settings put secure show_ime_with_hard_keyboard 1 # soft keyboard on screen while typing
adb shell settings put system show_touches 1                # white touch dots in the recording
adb shell settings put global window_animation_scale 1; adb shell settings put global transition_animation_scale 1; adb shell settings put global animator_duration_scale 1
read -r W H < <(adb shell wm size | grep -oE '[0-9]+x[0-9]+' | tail -1 | tr x ' ')
echo "display ${W}x${H}"
CX=$((W / 2))

# ---- helpers -------------------------------------------------------------------------------------------------------
cat > /tmp/find_xy.py <<'PY'
# find_xy.py <label> [n] [box]: prints "x y" (or, with a third arg, "x1 y1 x2 y2") of the n-th (0-based) node whose text or content-desc matches (exact, then prefix, then contains). Reads the uiautomator XML on stdin.
import sys, re, xml.etree.ElementTree as ET
label, n = sys.argv[1].lower(), int(sys.argv[2])
s = sys.stdin.read()
s = s[: s.rfind("</hierarchy>") + 12]
try: root = ET.fromstring(s)
except Exception: sys.exit(1)
hits = []
for e in root.iter("node"):
    t, d = (e.get("text") or "").lower(), (e.get("content-desc") or "").lower()
    m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", e.get("bounds") or "")
    if not m: continue
    x1, y1, x2, y2 = map(int, m.groups())
    if x2 <= x1 or y2 <= y1: continue
    sc = 0 if label in (t, d) else 1 if t.startswith(label) or d.startswith(label) else 2 if label in t or label in d else None
    if sc is not None: hits.append((sc, len(hits), (x1 + x2) // 2, (y1 + y2) // 2, x1, y1, x2, y2))
if not hits: sys.exit(1)
best = min(h[0] for h in hits)
pick = [h for h in hits if h[0] == best]
h = pick[min(n, len(pick) - 1)]
print(*(h[4:] if len(sys.argv) > 3 else h[2:4]))
PY
DUMPN=0; CLIP=init
dump() { # UI hierarchy to $OUT/dumps (kept as an artifact) and /tmp/ui.xml
  local i
  for i in 1 2 3; do
    adb exec-out uiautomator dump /dev/tty 2>/dev/null > /tmp/ui.xml
    grep -q "</hierarchy>" /tmp/ui.xml && break
    sleep 1
  done
  DUMPN=$((DUMPN + 1)); cp /tmp/ui.xml "$OUT/dumps/$CLIP-$DUMPN-${1:-dump}.xml"
}
xy_of() { dump "${1// /_}"; python3 /tmp/find_xy.py "$1" "${2:-0}" < /tmp/ui.xml; }
tap_text() { # tap_text "Transcript" [n]
  local xy; xy=$(xy_of "$1" "${2:-0}")
  if [ -z "$xy" ]; then echo "MISS tap_text '$1'"; return 1; fi
  echo "tap '$1' at $xy"; adb shell input tap $xy; sleep "${3:-1.2}"
}
has_text() { dump "has_${1// /_}"; python3 /tmp/find_xy.py "$1" 0 < /tmp/ui.xml > /dev/null; }
swipe_up()   { adb shell input swipe $CX $((H * 72 / 100)) $CX $((H * 30 / 100)) "${1:-650}"; sleep "${2:-1}"; }  # scrolls the content down
swipe_down() { adb shell input swipe $CX $((H * 30 / 100)) $CX $((H * 72 / 100)) "${1:-650}"; sleep "${2:-1}"; }
launch() { # launch <screen> <theme> [tab]
  if [ "$2" = dark ]; then adb shell cmd uimode night yes; else adb shell cmd uimode night no; fi
  adb shell am force-stop "$PKG"
  adb shell am start -W -n "$PKG/.MainActivity" --ez demo true --es screen "$1" --es theme "$2" ${3:+--es tab "$3"} > /dev/null
  sleep 4.5
  dump launch
  if grep -q "isn't responding" /tmp/ui.xml; then adb shell input keyevent KEYCODE_DPAD_DOWN KEYCODE_DPAD_DOWN KEYCODE_ENTER; sleep 3; fi
}
rec_start() { # rec_start <clip-name>: screenrecord runs in the background (time-limit is only a safety net)
  CLIP=$1; DUMPN=0
  adb shell rm -f "/sdcard/$CLIP.mp4"
  adb shell screenrecord --bit-rate 8000000 --time-limit 40 "/sdcard/$CLIP.mp4" &
  REC=$!
  sleep 0.8
}
rec_stop() {
  sleep 1
  adb shell pkill -INT screenrecord || true   # SIGINT finalizes the mp4
  wait "$REC" 2>/dev/null
  sleep 1
  adb pull "/sdcard/$CLIP.mp4" "$OUT/$CLIP.mp4" > /dev/null && echo "clip $CLIP $(du -k "$OUT/$CLIP.mp4" | cut -f1) KB"
}
typewords() { local w; for w in "$@"; do adb shell input text "$w"; sleep 0.1; done; } # each arg is one input call, use %s for spaces

# ---- clips (android-v2: the motion pass: press scale, sliding tab rule, field focus, staggered entrances) ----------
key_back() { adb shell input keyevent KEYCODE_BACK; sleep "${1:-1.2}"; }
clip_welcome() { # $1 theme. Entrance stagger, then sign-in: field focus rings, typing, switch to sign-up and back.
  launch welcome "$1"; adb shell am force-stop "$PKG"; rec_start "01-welcome-signin-$1"
  adb shell am start -W -n "$PKG/.MainActivity" --ez demo true --es screen welcome --es theme "$1" > /dev/null
  sleep 2.5
  tap_text "I have an account" 0 1.8
  tap_text "Email" 0 0.8; typewords "maya@example.com"; sleep 0.6
  tap_text "Password" 0 0.8; typewords "practice2026"; sleep 0.6
  key_back 0.8   # hide the keyboard
  tap_text "Create an account" 0 1.8
  tap_text "Sign in" 0 1.5
  rec_stop
}
clip_tabs() { # $1 theme. Today scroll, then every tab: the accent rule slides and takes the skill's colour.
  launch today "$1"; rec_start "02-tabs-$1"
  sleep 1; swipe_up 900 1.2; swipe_down 700 1
  for t in Listening Reading Writing Speaking Today; do tap_text "$t" 99 1.6; done  # 99 = last match: the bottom bar, not the Today card rows
  rec_stop
}
clip_bank() { # chips animate their fill, the search field shows its focus ring
  launch bank light; rec_start "03-bank-chips-light"
  sleep 1; tap_text "Part 1" 0 1; tap_text "Books" 0 1; tap_text "All parts" 0 1; tap_text "All topics" 0 1
  tap_text "Search prompts" 0 0.8; typewords "home"; sleep 1.2; key_back 1
  rec_stop
}
clip_mock() { # the Today hero: press Resume, the mock hub pushes in, back pops out
  launch today light; rec_start "04-mock-resume-light"
  sleep 1; tap_text "Resume" 0 2.2; key_back 1.6
  tap_text "Your bands" 0 0.3; swipe_up 900 1.2; swipe_down 900 1
  rec_stop
}
clip_settings() { # segmented control thumb, then the delete sheet with its password field
  launch settings light; rec_start "05-settings-light"
  sleep 1; tap_text "Dark" 0 1.4; tap_text "Light" 0 1.4; tap_text "System" 0 1.2
  rec_stop
}

clip_splash() { # $1 theme. Cold start: system splash pen-draws the N, then the in-app lockup sequence
  if [ "$1" = dark ]; then adb shell cmd uimode night yes; else adb shell cmd uimode night no; fi
  adb shell am force-stop "$PKG"; rec_start "00-splash-$1"
  adb shell am start -W -n "$PKG/.MainActivity" --ez demo true --es screen splash --es theme "$1" > /dev/null
  sleep 9  # the emulator's cold start alone takes ~5 s
  rec_stop
}

ONLY=${ONLY:-}  # e.g. ONLY="clip_tabs:dark clip_bank" for a quick re-run
if [ -n "$ONLY" ]; then for c in $ONLY; do ${c%%:*} ${c#*:}; done; exit 0; fi
clip_splash light
clip_splash dark
clip_welcome light
clip_tabs light
clip_bank
clip_mock
clip_settings
clip_welcome dark
clip_tabs dark
