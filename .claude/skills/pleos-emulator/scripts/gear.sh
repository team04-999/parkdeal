#!/usr/bin/env bash
# 기어를 바꾸고, 실제로 바뀌었는지 확인한다.
#   bash gear.sh Park|Reverse|Neutral|Drive
#
# Virtual Console(에뮬레이터 안 :8088)은 브라우저 세션 하트비트가 3초 안에 없으면
# `emulator_inactive`(503)로 명령을 거부한다. 그래서 명령 전후로 1초마다 하트비트를 보낸다.
set -euo pipefail

gear=${1:?사용법: gear.sh Park|Reverse|Neutral|Drive}
case "$gear" in Park|Reverse|Neutral|Drive) ;; *) echo "알 수 없는 기어: $gear" >&2; exit 2 ;; esac

VC=http://localhost:8088
adb forward tcp:8088 tcp:8088 >/dev/null

( while true; do curl -s -o /dev/null -X POST "$VC/browser/session?client=cli"; sleep 1; done ) &
hb=$!
trap 'kill $hb 2>/dev/null || true' EXIT
sleep 1

code=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$VC/vehicle/gear?gear=$gear")
[ "$code" = 200 ] || { echo "기어 명령 실패: HTTP $code" >&2; exit 1; }

# 명령이 200 이어도 실제 기어가 바뀌었는지는 상태로 확인한다
for _ in $(seq 1 10); do
  actual=$(curl -s "$VC/vehicle/state" | python3 -c 'import json,sys; print(json.load(sys.stdin)["gear"]["actual"]["value"])')
  [ "$actual" = "$gear" ] && { echo "기어: $actual"; exit 0; }
  sleep 1
done
echo "기어가 $gear 로 바뀌지 않았다 (현재 $actual)" >&2
exit 1
