#!/usr/bin/env bash
# Virtual Console(에뮬레이터 안 :8088) 차량 상태 읽기·쓰기.
#   bash vc.sh get [경로]                  예: get body.door_locks.all   (경로 없으면 전체 JSON)
#   bash vc.sh set <엔드포인트> k=v ...    예: set body doors_locked=true
#                                              set seat area=driver kind=occupied value=false
#   bash vc.sh catalog [검색어]            기능 목록과 쓰기 엔드포인트·필드
#
# 이 HTTP API 는 공식 문서에 없다(문서는 브라우저 UI 만 다룬다). 에뮬레이터 버전이 바뀌면
# 깨질 수 있으니, 엔드포인트와 필드는 외우지 말고 catalog 로 확인한다.
set -euo pipefail

VC=http://localhost:8088
adb forward tcp:8088 tcp:8088 >/dev/null

cmd=${1:?사용법: vc.sh get|set|catalog ...}; shift || true

case "$cmd" in
  get)
    curl -fsS "$VC/vehicle/state" | python3 -c '
import json, sys
d = json.load(sys.stdin)
path = sys.argv[1] if len(sys.argv) > 1 else ""
for key in filter(None, path.split(".")):
    d = d[key]
print(json.dumps(d, ensure_ascii=False, indent=None if path else 2))' "${1:-}"
    ;;
  set)
    endpoint=${1:?엔드포인트가 필요하다 (예: body, seat, controls)}; shift
    [ $# -gt 0 ] || { echo "k=v 가 하나 이상 필요하다" >&2; exit 2; }
    query=$(IFS='&'; echo "$*")
    # 하트비트가 3초 안에 없으면 쓰기가 emulator_inactive(503)로 거부된다
    curl -fsS -o /dev/null -X POST "$VC/browser/session?client=cli"
    code=$(curl -s -o /dev/null -w '%{http_code}' -X POST "$VC/vehicle/$endpoint?$query")
    [ "$code" = 200 ] || { echo "실패: POST /vehicle/$endpoint?$query → HTTP $code" >&2; exit 1; }
    echo "OK: /vehicle/$endpoint?$query"
    ;;
  catalog)
    curl -fsS "$VC/vehicle/catalog" | python3 -c '
import json, sys
q = sys.argv[1].lower() if len(sys.argv) > 1 else ""
for f in json.load(sys.stdin)["features"]:
    if q and q not in json.dumps(f).lower():
        continue
    c = f.get("control", {})
    field = c.get("field") or c.get("field_by_area")
    areas = [a["value"] for a in f.get("areas", [])]
    opts = [o["value"] for o in c.get("options", [])] or [c.get("min"), c.get("max")]
    print(f"{f['"'"'id'"'"']:26} {f.get('"'"'write_endpoint'"'"')}  field={field}  values={opts}" + (f"  areas={areas}" if areas else "") + (f"  params={c['"'"'params'"'"']}" if c.get("params") else ""))' "${1:-}"
    ;;
  *) echo "알 수 없는 명령: $cmd" >&2; exit 2 ;;
esac
