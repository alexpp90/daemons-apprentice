#!/bin/bash
# Runs the instrumented tests on ONE emulator, never on a real phone.
# Usage: tools/device-tests.sh emulator-5556 [test class or class#method]
set -u
SER=${1:?emulator serial, e.g. emulator-5556}; ONLY=${2:-}
case $SER in emulator-*) ;; *) echo "refusing to run on $SER: emulators only (real phones need the owner's OK)"; exit 2;; esac
cd "$(dirname "$0")/.." || exit 2
ADB="${ANDROID_HOME:-$HOME/Android/Sdk}/platform-tools/adb -s $SER"
APK=app/build/outputs/apk/debug/app-debug.apk
TAPK=app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
$ADB install -r -g "$APK" >/dev/null && $ADB install -r -g "$TAPK" >/dev/null || { echo "install failed"; exit 2; }
# A freshly booted emulator is busy for a while (its launcher may not even bring an app to the
# front): let it settle until it has been up for 150 s. A warm emulator does not wait.
UP=$($ADB shell cut -d. -f1 /proc/uptime | tr -d '\r')
if [ "${UP:-999}" -lt 150 ] 2>/dev/null; then echo "emulator just booted (${UP} s): settling for $((150 - UP)) s"; sleep $((150 - UP)); fi
# A freshly booted emulator may have its screen off or locked: wake it, unlock it, keep it on.
$ADB shell input keyevent KEYCODE_WAKEUP >/dev/null 2>&1
$ADB shell wm dismiss-keyguard >/dev/null 2>&1
$ADB shell svc power stayon true >/dev/null 2>&1
# A cold emulator's own launcher often "isn't responding" for a while; its dialog would cover the game.
$ADB shell settings put global hide_error_dialogs 1 >/dev/null 2>&1
ARGS=""; [ -n "$ONLY" ] && ARGS="-e class io.github.alexpp90.daemonsapprentice.$ONLY"
START=$(date +%s)
OUT=$($ADB shell am instrument -w -r $ARGS io.github.alexpp90.daemonsapprentice.test/androidx.test.runner.AndroidJUnitRunner)
echo "$OUT" | grep -E 'INSTRUMENTATION_STATUS: (class|test)=|INSTRUMENTATION_STATUS_CODE: -?[0-9]' | paste - - - 2>/dev/null | sed -E 's/INSTRUMENTATION_STATUS: (class|test)=//g; s/INSTRUMENTATION_STATUS_CODE: 1/  started/; s/INSTRUMENTATION_STATUS_CODE: 0/  ok/' | grep -v started | sed 's/INSTRUMENTATION_STATUS_CODE: -2/  FAILED/; s/INSTRUMENTATION_STATUS_CODE: -3/  skipped (assumption)/' 
echo "$OUT" | grep -E '^(OK|FAILURES|Tests run)' 
echo "$OUT" | grep -A8 'INSTRUMENTATION_STATUS: stack=' | grep -vE '^\s+at (androidx|org\.junit|java|kotlin|android\.app)' | head -40
echo "time: $(( $(date +%s) - START )) s on $SER (Android $($ADB shell getprop ro.build.version.release | tr -d '\r'))"
echo "$OUT" | grep -q '^OK ('
