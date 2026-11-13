#!/usr/bin/env python3
"""데모 목적지의 서버 응답을 app/src/main/assets/api-snapshot 에 저장한다.

서버와 캐시가 모두 없을 때(시연장 인터넷 끊김) 앱이 이 스냅샷으로 동작한다.
파일 이름은 core 의 ApiRequest.cacheFile 규칙과 같아야 한다 — SnapshotAssetsTest 가 확인한다.

    python3 scripts/snapshot_api.py "조이치과" 37.482052 126.942983 [이름 위도 경도 ...]

이 기기의 DNS 가 아직 이름을 못 풀면 접속 IP 만 지정할 수 있다 (curl --resolve 와 같다. TLS 는 이름으로 확인).

    PARKDEAL_API_RESOLVE=104.21.15.73 python3 scripts/snapshot_api.py ...
"""
import hashlib
import json
import os
import re
import socket
import sys
import urllib.parse
import urllib.request
from pathlib import Path

HOST = "parkdeal-api.daeuk.cloud"
BASE = f"https://{HOST}"

_resolve = os.environ.get("PARKDEAL_API_RESOLVE")
if _resolve:
    _getaddrinfo = socket.getaddrinfo

    def _pinned(host, *args, **kwargs):
        return _getaddrinfo(_resolve if host == HOST else host, *args, **kwargs)

    socket.getaddrinfo = _pinned
OUT = Path(__file__).resolve().parent.parent / "app/src/main/assets/api-snapshot"


def coord(v: float) -> str:
    return f"{v:.4f}"


def cache_file(key: str) -> str:
    return hashlib.sha256(key.encode()).hexdigest()[:16] + ".json"


def fetch(path: str) -> str:
    # Cloudflare 가 Python 기본 User-Agent(Python-urllib)를 봇으로 막는다(오류 1010)
    req = urllib.request.Request(BASE + path, headers={"User-Agent": "parkdeal-snapshot/1"})
    with urllib.request.urlopen(req, timeout=10) as r:
        body = r.read().decode()
    json.loads(body)  # 형식 확인
    return body


def snapshot(name: str, lat: float, lng: float) -> None:
    clean = name.strip()
    dest_path = f"/v1/destination?lat={coord(lat)}&lng={coord(lng)}&name={urllib.parse.quote_plus(clean)}"
    dest_key = f"destination|{coord(lat)}|{coord(lng)}|{re.sub(r'\s+', '', clean)}"
    lots_path = f"/v1/lots?lat={coord(lat)}&lng={coord(lng)}&radius=800"
    lots_key = f"lots|{coord(lat)}|{coord(lng)}|800"
    for path, key in ((dest_path, dest_key), (lots_path, lots_key)):
        f = OUT / cache_file(key)
        f.write_text(fetch(path))
        print(f"{name}: {path} → {f.name}")


def main(argv: list[str]) -> int:
    args = argv[1:]
    if not args or len(args) % 3:
        print(__doc__, file=sys.stderr)
        return 2
    OUT.mkdir(parents=True, exist_ok=True)
    for i in range(0, len(args), 3):
        snapshot(args[i], float(args[i + 1]), float(args[i + 2]))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
