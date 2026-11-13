#!/usr/bin/env bash
# Pleos Connect 에뮬레이터를 cold boot 로 띄우고 부팅 완료까지 기다린다.
#   bash boot.sh [AVD 이름, 기본 Pleos_Connect]
#
# quick boot(스냅샷)로 띄우면 VHAL 을 쓸 수 없고, com.android.car·systemui 가
# 크래시 루프에 빠질 수 있다. 항상 스냅샷을 읽지도 쓰지도 않는다.
set -euo pipefail

avd=${1:-Pleos_Connect}
EMU=${ANDROID_HOME:-$HOME/Library/Android/sdk}/emulator/emulator

if adb get-state >/dev/null 2>&1; then
  echo "이미 연결된 기기가 있다: $(adb devices | sed -n 2p)"
else
  log=${TMPDIR:-/tmp}/pleos-emulator.log
  nohup "$EMU" -avd "$avd" -no-snapshot-load -no-snapshot-save >"$log" 2>&1 &
  echo "에뮬레이터 시작 (로그: $log)"
fi

adb wait-for-device
for _ in $(seq 1 180); do
  [ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = 1 ] && { echo "부팅 완료"; exit 0; }
  sleep 2
done
echo "6분 안에 부팅이 끝나지 않았다" >&2
exit 1
