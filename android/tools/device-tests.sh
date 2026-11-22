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
ARGS=""; [ -n "$ONLY" ] && ARGS="-e class io.github.alexpp90.daemonsapprentice.$ONLY"
START=$(date +%s)
OUT=$($ADB shell am instrument -w -r $ARGS io.github.alexpp90.daemonsapprentice.test/androidx.test.runner.AndroidJUnitRunner)
echo "$OUT" | grep -E 'INSTRUMENTATION_STATUS: (class|test)=|INSTRUMENTATION_STATUS_CODE: -?[0-9]' | paste - - - 2>/dev/null | sed -E 's/INSTRUMENTATION_STATUS: (class|test)=//g; s/INSTRUMENTATION_STATUS_CODE: 1/  started/; s/INSTRUMENTATION_STATUS_CODE: 0/  ok/' | grep -v started | sed 's/INSTRUMENTATION_STATUS_CODE: -2/  FAILED/; s/INSTRUMENTATION_STATUS_CODE: -3/  skipped (assumption)/' 
echo "$OUT" | grep -E '^(OK|FAILURES|Tests run)' 
echo "$OUT" | grep -A8 'INSTRUMENTATION_STATUS: stack=' | grep -vE '^\s+at (androidx|org\.junit|java|kotlin|android\.app)' | head -40
echo "time: $(( $(date +%s) - START )) s on $SER (Android $($ADB shell getprop ro.build.version.release | tr -d '\r'))"
echo "$OUT" | grep -q '^OK ('
