#!/usr/bin/env bash
set -uo pipefail
mkdir -p smoke-artifacts

# This APK is x86_64 for the emulator only; the regular workflow builds ARM APKs.
./gradlew --no-daemon -P emulatorBuild=true connectedDebugAndroidTest 2>&1 | tee smoke-artifacts/instrumentation.log
status=${PIPESTATUS[0]}

# Preserve the device log even when the test fails. A screenshot is for inspection,
# not proof that navigation or physical-device behavior is correct.
adb logcat -d -v threadtime '*:W' > smoke-artifacts/logcat.txt || true
if [ "$status" -eq 0 ]; then
  adb shell am start -n com.iranamap.real/.MapActivity >/dev/null 2>&1 || true
  sleep 10
  adb shell screencap -p /sdcard/iranamap-smoke.png >/dev/null 2>&1 || true
  adb pull /sdcard/iranamap-smoke.png smoke-artifacts/screenshot.png >/dev/null 2>&1 || true
fi
exit "$status"
