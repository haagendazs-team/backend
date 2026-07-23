#!/usr/bin/env bash
# k6/notification/heap-guard.sh
# sustain 부하테스트 중 서버 JVM 힙 사용률을 감시하다가 임계치 초과 시 서버를 강제 종료한다.
#
# 사용법:
#   ./k6/notification/heap-guard.sh &                  # 백그라운드로 감시 시작 (기본 임계치 90%)
#   THRESHOLD=80 ./k6/notification/heap-guard.sh &      # 임계치 환경변수로 지정
#   ./k6/notification/heap-guard.sh 85 &               # 임계치 인자로 지정
#   PID=12345 ./k6/notification/heap-guard.sh &         # 대상 PID 직접 지정 (기본: SportsifyApplication 자동 탐색)
#   K6_PID=23456 ./k6/notification/heap-guard.sh &      # 서버 종료 시 같이 죽일 k6 프로세스도 지정 (run.sh 용)
#
# run.sh 가 이 스크립트의 시작/종료를 자동으로 관리하므로 직접 실행할 필요는 없다.
# 종료: kill %1  (또는 jobs 로 PID 확인 후 kill)

set -uo pipefail

# 인자 > 환경변수 > 기본값(75) 순으로 적용
THRESHOLD="${1:-${THRESHOLD:-92}}"
INTERVAL="${INTERVAL:-5}"
CONTAINER="${CONTAINER:-}"
if [[ -z "$CONTAINER" ]]; then
    CONTAINER="$(docker ps --format '{{.Names}}' | grep -E 'notification' | head -1)"
fi
K6_PID="${K6_PID:-}"
MAX_READ_FAILURES="${MAX_READ_FAILURES:-10}"

_read_fail_count=0

# 컨테이너 존재 확인
if ! docker inspect "$CONTAINER" &>/dev/null; then
    echo "[heap-guard] 컨테이너 '${CONTAINER}'를 찾을 수 없습니다." >&2
    exit 1
fi

echo "[heap-guard] container=${CONTAINER} 감시 시작 (임계치 ${THRESHOLD}%, 주기 ${INTERVAL}s)"

APP_URL="${APP_URL:-http://localhost:8081}"

while true; do
    # 컨테이너가 살아있는지 확인
    STATUS="$(docker inspect -f '{{.State.Running}}' "$CONTAINER" 2>/dev/null)"
    if [[ "$STATUS" != "true" ]]; then
        echo "[heap-guard] 컨테이너가 종료되었습니다. 감시를 중단합니다."
        exit 0
    fi

    # docker stats로 컨테이너 메모리 사용률 조회 (고부하에서도 안정적)
    _stats="$(docker stats --no-stream --format '{{.MemUsage}}' "$CONTAINER" 2>/dev/null)"
    # 형식: "1.23GiB / 4GiB" 또는 "512MiB / 4GiB"
    USED_M="$(echo "$_stats" | python3 -c "
import sys, re
s=sys.stdin.read().strip()
m=re.match(r'([\d.]+)(\w+)\s*/\s*([\d.]+)(\w+)', s)
if not m: print(0,0); exit()
def to_mb(v,u):
    v=float(v); u=u.lower()
    if 'gib' in u or 'gb' in u: return int(v*1024)
    if 'mib' in u or 'mb' in u: return int(v)
    if 'kib' in u or 'kb' in u: return int(v/1024)
    return int(v/1024/1024)
print(to_mb(m[1],m[2]), to_mb(m[3],m[4]))
" | awk '{print $1}')"
    MAX_M="$(echo "$_stats" | python3 -c "
import sys, re
s=sys.stdin.read().strip()
m=re.match(r'([\d.]+)(\w+)\s*/\s*([\d.]+)(\w+)', s)
if not m: print(0,0); exit()
def to_mb(v,u):
    v=float(v); u=u.lower()
    if 'gib' in u or 'gb' in u: return int(v*1024)
    if 'mib' in u or 'mb' in u: return int(v)
    if 'kib' in u or 'kb' in u: return int(v/1024)
    return int(v/1024/1024)
print(to_mb(m[1],m[2]), to_mb(m[3],m[4]))
" | awk '{print $2}')"

    if [[ -z "$USED_M" || -z "$MAX_M" || "$MAX_M" -le 0 ]]; then
        (( _read_fail_count++ )) || true
        echo "[heap-guard] 메모리 정보를 읽지 못했습니다. ${INTERVAL}s 후 재시도. (${_read_fail_count}/${MAX_READ_FAILURES})" >&2
        if (( _read_fail_count >= MAX_READ_FAILURES )); then
            echo "[heap-guard] 연속 ${MAX_READ_FAILURES}회 읽기 실패 — 부하테스트를 종료합니다." >&2
            if [[ -n "$K6_PID" ]] && kill -0 "$K6_PID" 2>/dev/null; then
                kill -15 "$K6_PID"
            fi
            exit 1
        fi
        sleep "$INTERVAL"
        continue
    fi

    _read_fail_count=0

    USAGE_PCT=$(( USED_M * 100 / MAX_M ))
    echo "[heap-guard] mem used=${USED_M}M / max=${MAX_M}M (${USAGE_PCT}%)"

    if (( USAGE_PCT >= THRESHOLD )); then
        echo "[heap-guard] 임계치(${THRESHOLD}%) 초과 감지 (${USAGE_PCT}%). k6 종료 (컨테이너 유지)."

        if [[ -n "$K6_PID" ]] && kill -0 "$K6_PID" 2>/dev/null; then
            echo "[heap-guard] k6 PID=${K6_PID} 종료."
            kill -15 "$K6_PID"
        fi

        echo "[heap-guard] 종료 완료."
        exit 0
    fi

    sleep "$INTERVAL"
done
