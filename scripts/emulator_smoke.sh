#!/usr/bin/env bash
set -uo pipefail
mkdir -p smoke-artifacts

# This APK is x86_64 for the emulator only; the regular workflow builds ARM APKs.
./gradlew --no-daemon -P emulatorBuild=true connectedDebugAndroidTest 2>&1 | tee smoke-artifacts/instrumentation.log
status=${PIPESTATUS[0]}

if [ "$status" -eq 0 ]; then
  # The test runner can uninstall the target app after tests; explicitly reinstall
  # and verify the real app is in the foreground before accepting a screenshot.
  apk=app/build/outputs/apk/debug/app-debug.apk
  if ! adb install -r -g "$apk" >smoke-artifacts/install.txt 2>&1; then
    echo 'Failed to reinstall the app for the launch check.' >&2
    status=1
  elif ! adb shell am start -W -n com.iranamap.real/.MapActivity >smoke-artifacts/launch.txt 2>&1; then
    echo 'Failed to launch the app.' >&2
    status=1
  else
    sleep 10
    adb shell dumpsys activity activities >smoke-artifacts/activities.txt 2>&1 || true
    adb shell screencap -p /sdcard/iranamap-smoke.png >/dev/null 2>&1 || true
    adb pull /sdcard/iranamap-smoke.png smoke-artifacts/screenshot.png >/dev/null 2>&1 || true
    if ! adb shell pidof com.iranamap.real >smoke-artifacts/app-pid.txt 2>&1; then
      echo 'The app process is no longer running after launch.' >&2
      status=1
    elif ! grep -E 'mResumedActivity.*com\.iranamap\.real|topResumedActivity.*com\.iranamap\.real|Resumed:.*com\.iranamap\.real' smoke-artifacts/activities.txt >/dev/null; then
      echo 'The app was not the resumed foreground activity after launch.' >&2
      status=1
    fi
  fi
fi

# Preserve the device log even on failure, including the separate launch check.
adb logcat -d -v threadtime '*:W' > smoke-artifacts/logcat.txt || true
exit "$status"
