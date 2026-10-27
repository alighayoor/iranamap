#!/usr/bin/env bash
set -uo pipefail
mkdir -p smoke-artifacts

# The QA package can coexist with the original; Kotlin namespace stays unchanged.
app_id="${IRANAMAP_APP_ID:-com.iranamap.real}"
base_id="com.iranamap.real"
if [[ ! "$app_id" =~ ^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z][A-Za-z0-9_]*)+$ ]]; then
  echo 'Invalid application ID for emulator smoke tests.' >&2
  exit 2
fi
printf 'target_package=%s\nbase_package=%s\n' "$app_id" "$base_id" >smoke-artifacts/packages.txt
status=0

if [ "$app_id" != "$base_id" ]; then
  # Install a debug build of the normal package first and write a sentinel to its
  # private storage. Installing/testing QA must not remove the package or data.
  ./gradlew --no-daemon -PemulatorBuild=true "-PappId=$base_id" assembleDebug 2>&1 | tee smoke-artifacts/coinstall-build.log
  status=${PIPESTATUS[0]}
  if [ "$status" -eq 0 ]; then
    if ! adb install -r -g app/build/outputs/apk/debug/app-debug.apk >smoke-artifacts/coinstall-install.txt 2>&1; then
      echo 'Failed to install the original package for the co-install test.' >&2
      status=1
    elif ! adb shell "run-as $base_id sh -c 'mkdir -p files && printf preserved > files/qa-preserved.txt'"; then
      echo 'Failed to prepare original-package data for the co-install test.' >&2
      status=1
    fi
  fi
fi

if [ "$status" -eq 0 ]; then
  # This APK is x86_64 for the emulator only; the regular workflow builds ARM APKs.
  ./gradlew --no-daemon -PemulatorBuild=true "-PappId=$app_id" connectedDebugAndroidTest 2>&1 | tee smoke-artifacts/instrumentation.log
  status=${PIPESTATUS[0]}
fi

if [ "$status" -eq 0 ]; then
  # The test runner can uninstall the target app after tests; explicitly reinstall
  # and verify the real app is in the foreground before accepting a screenshot.
  apk=app/build/outputs/apk/debug/app-debug.apk
  if ! adb install -r -g "$apk" >smoke-artifacts/install.txt 2>&1; then
    echo 'Failed to reinstall the app for the launch check.' >&2
    status=1
  elif ! adb shell am start -W -n "$app_id/com.iranamap.real.MapActivity" >smoke-artifacts/launch.txt 2>&1; then
    echo 'Failed to launch the app.' >&2
    status=1
  else
    sleep 10
    adb shell dumpsys activity activities >smoke-artifacts/activities.txt 2>&1 || true
    if ! adb shell screencap -p /sdcard/iranamap-smoke.png >/dev/null 2>&1 || \
       ! adb pull /sdcard/iranamap-smoke.png smoke-artifacts/screenshot.png >/dev/null 2>&1; then
      echo 'Could not capture the actual app screenshot.' >&2
      status=1
    fi
    if ! adb shell pidof "$app_id" >smoke-artifacts/app-pid.txt 2>&1; then
      echo 'The app process is no longer running after launch.' >&2
      status=1
    elif ! grep -E 'mResumedActivity|topResumedActivity|Resumed:' smoke-artifacts/activities.txt | grep -F "$app_id/" >/dev/null; then
      echo 'The app was not the resumed foreground activity after launch.' >&2
      status=1
    fi
  fi
fi

if [ "$status" -eq 0 ] && [ "$app_id" != "$base_id" ]; then
  adb shell pm path "$base_id" >smoke-artifacts/coinstall-package.txt 2>&1 || true
  adb shell "run-as $base_id cat files/qa-preserved.txt" 2>/dev/null | tr -d '\r' >smoke-artifacts/coinstall-data.txt
  if ! grep -q '^package:' smoke-artifacts/coinstall-package.txt || \
     [ "$(cat smoke-artifacts/coinstall-data.txt)" != 'preserved' ]; then
    echo 'The original package or its private data did not survive the QA install.' >&2
    status=1
  fi
fi

# Preserve the device log even on failure, including the separate launch check.
adb logcat -d -v threadtime '*:W' >smoke-artifacts/logcat.txt || true
exit "$status"
