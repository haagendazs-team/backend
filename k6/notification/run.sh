#!/usr/bin/env bash
# k6 알림 부하 테스트 실행 스크립트
#
# 사용법:
#   ./k6/notification/run.sh                        # connect + sustain + send + receive 순차 실행 (기본 3000 VU)
#   ./k6/notification/run.sh connect                # 연결 수립 테스트 — TTFB, 수락 성공률
#   ./k6/notification/run.sh sustain                # 연결 유지 테스트 — CCU 수용량
#   ./k6/notification/run.sh send                   # 이벤트 발행 테스트 — TPS 내성, 브로드캐스트 포화 탐색
#   ./k6/notification/run.sh receive                # 이벤트 수신 전파 테스트 — 3K CCU 전파 완전성
#   ./k6/notification/run.sh connect 1000           # VU 수 직접 지정
#   ./k6/notification/run.sh connect --noCleanUp    # 테스트 후 seed 데이터 유지 (디버깅용)
#   ./k6/notification/run.sh prometheus-verify      # Prometheus 메트릭 적재 검증 (최대 100 VU)
#
# 환경 변수 (선택):
#   BASE_URL      — 테스트 대상 서버          (기본: https://localhost:8443)
#   DB_CONTAINER  — PostgreSQL 컨테이너 이름  (기본: sportsify-postgres)
#   DB_NAME       — 데이터베이스 이름         (기본: sportsify)
#   DB_USER       — DB 사용자                (기본: sportsify)
#
# OS 사전 설정 (3K 실행 시 필수):
#   macOS:
#     ulimit -n 131072
#     sudo sysctl -w kern.maxfilesperproc=131072
#     sudo sysctl -w kern.maxfiles=131072
#   Linux:
#     ulimit -n 131072
#     sudo sysctl -w fs.file-max=200000

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

BASE_URL="${BASE_URL:-http://localhost:8080}"
MEMBER_URL="${MEMBER_URL:-http://localhost:8084}"
DB_CONTAINER="${DB_CONTAINER:-haagendazs-postgres}"
DB_NAME="${DB_NAME:-haagendazs}"
DB_USER="${DB_USER:-${POSTGRES_USER:-haagendazs}}"
TOKENS_FILE="${SCRIPT_DIR}/tokens.json"

psql_exec() {
    docker exec -i "$DB_CONTAINER" psql -U "$DB_USER" -d "$DB_NAME" "$@"
}

TARGET="${1:-all}"
NO_CLEANUP=false
_USER_VUS="24000"
for arg in "$@"; do
    [[ "$arg" == "--noCleanUp" ]] && NO_CLEANUP=true
    [[ "$arg" =~ ^[0-9]+$ ]]     && _USER_VUS="$arg"
done

VUS="${_USER_VUS:-24000}"
SUSTAIN_START_VUS="${SUSTAIN_START_VUS:-6000}"
SUSTAIN_MAX_VUS="${SUSTAIN_MAX_VUS:-$VUS}"

SEED_OFFSET=10000

# ── OS fd 한계 경고 ────────────────────────────────────────────
check_fd_limit() {
    local current_limit
    current_limit=$(ulimit -n 2>/dev/null || echo 0)
    local required=$(( VUS + 2000 ))

    if [[ "$current_limit" -lt "$required" ]]; then
        echo ""
        echo "╔══════════════════════════════════════════════════════════════╗"
        echo "║  경고: OS fd 한계 부족 (현재: ${current_limit}, 필요: ${required}+)       ║"
        echo "║                                                              ║"
        echo "║  macOS:                                                      ║"
        echo "║    ulimit -n 131072     * 2                                       ║"
        echo "║    sudo sysctl -w kern.maxfilesperproc=131072                 ║"
        echo "║    sudo sysctl -w kern.maxfiles=131072                        ║"
        echo "║                                                              ║"
        echo "║  Linux:                                                      ║"
        echo "║    ulimit -n 131072                                           ║"
        echo "║    sudo sysctl -w fs.file-max=200000                         ║"
        echo "║                                                              ║"
        echo "║  이 상태로 실행하면 연결 실패가 서버 문제처럼 보입니다.     ║"
        echo "╚══════════════════════════════════════════════════════════════╝"
        echo ""
        read -r -p "계속 진행하시겠습니까? (y/N): " confirm
        [[ "${confirm:-N}" =~ ^[Yy]$ ]] || exit 1
    fi
}
check_fd_limit

HEAP_GUARD_PID=""

stop_heap_guard() {
    if [[ -n "$HEAP_GUARD_PID" ]] && kill -0 "$HEAP_GUARD_PID" 2>/dev/null; then
        kill "$HEAP_GUARD_PID" 2>/dev/null
    fi
    HEAP_GUARD_PID=""
}

cleanup() {
    stop_heap_guard
    rm -f "$TOKENS_FILE"
    [[ "$TARGET" == "sustain-direct" || "$TARGET" == "sse-v2-poc" ]] && return
    if [[ "$NO_CLEANUP" == true ]]; then
        echo ""
        echo "[cleanup 생략] seed 데이터가 DB에 남아 있습니다."
        echo "  수동 정리: docker exec -i $DB_CONTAINER psql -U $DB_USER -d $DB_NAME -v vus=$VUS -v seed_offset=$SEED_OFFSET -f k6/notification/seed_cleanup.sql"
        return
    fi
    echo ""
    echo "▶ [cleanup] seed_cleanup.sql 실행 중..."
    psql_exec -v vus="$VUS" -v seed_offset="$SEED_OFFSET" < "$SCRIPT_DIR/seed_cleanup.sql" \
        && echo "✔ cleanup 완료" \
        || echo "✘ cleanup 실패 (수동 확인 필요)"
}
trap cleanup EXIT

# sustain-direct, sse-v2-poc, send: DB seed / 토큰 불필요
# send — /module/notifications/publish 는 내부 API (인증 없음), memberId는 계산으로 생성
if [[ "$TARGET" != "sustain-direct" ]] && [[ "$TARGET" != "sse-v2-poc" ]] && [[ "$TARGET" != "send" ]]; then

# ── seed ──────────────────────────────────────────────────────
echo "▶ [seed] seed.sql 실행 중... (VUS=$VUS, SEED_OFFSET=$SEED_OFFSET)"
psql_exec -v vus="$VUS" -v seed_offset="$SEED_OFFSET" < "$SCRIPT_DIR/seed.sql"
echo "✔ seed 완료"
echo ""

# ── 토큰 사전 발급 (bulk API, 1000개씩 병렬) ─────────────────────
BULK_SIZE=1000
BATCH_PARALLEL=10
echo "▶ [tokens] ${VUS}개 토큰 사전 발급 중 (bulk ${BULK_SIZE}개씩, 병렬 ${BATCH_PARALLEL})..."
TEMP_DIR=$(mktemp -d)

fetch_bulk() {
    local batch_index="$1"
    local start_id=$(( SEED_OFFSET + batch_index * BULK_SIZE + 1 ))
    local result
    local retries=3
    for attempt in $(seq 1 $retries); do
        result=$(curl -sf -k --max-time 60 "${MEMBER_URL}/dev/token/bulk?startMemberId=${start_id}&count=${BULK_SIZE}&role=USER&expiryMs=2592000000")
        if [[ -n "$result" ]]; then
            echo "$result" > "${TEMP_DIR}/${batch_index}.json"
            return 0
        fi
        echo "  재시도 ${attempt}/${retries} startMemberId=${start_id}" >&2
        sleep 2
    done
    echo "✘ bulk 토큰 발급 실패 startMemberId=${start_id}" >&2
    exit 1
}
export -f fetch_bulk
export BASE_URL SEED_OFFSET TEMP_DIR BULK_SIZE

total_batches=$(( (VUS + BULK_SIZE - 1) / BULK_SIZE ))
seq 0 $(( total_batches - 1 )) | xargs -P "$BATCH_PARALLEL" -I{} bash -c 'fetch_bulk "$@"' _ {}

# 배치 순서대로 병합
python3 -c "
import json, os, sys
temp_dir = '${TEMP_DIR}'
total = ${total_batches}
vus   = ${VUS}
tokens = []
for i in range(total):
    with open(os.path.join(temp_dir, f'{i}.json')) as f:
        tokens.extend(json.load(f))
json.dump(tokens[:vus], sys.stdout)
" > "$TOKENS_FILE"

rm -rf "$TEMP_DIR"
echo "✔ 토큰 발급 완료 → tokens.json ($(python3 -c "import json; print(len(json.load(open('$TOKENS_FILE'))))")개)"
echo ""

# ── 토큰 만료 체크 및 재발급 ──────────────────────────────────
# tokens.json의 첫 번째 토큰 exp를 읽어 테스트 예상 소요 시간보다 충분하지 않으면 전체 재발급
check_and_refresh_tokens() {
    local required_sec="${1:-1200}"   # 기본 20분 여유

    local first_token
    first_token=$(python3 -c "import json; print(json.load(open('${TOKENS_FILE}'))[0]['token'])")

    local exp_sec
    exp_sec=$(python3 -c "
import base64, json, sys
token = '${first_token}'.split('.')[1]
# base64 패딩 보정
token += '=' * (-len(token) % 4)
payload = json.loads(base64.urlsafe_b64decode(token))
print(payload['exp'])
" 2>/dev/null) || { echo "[tokens] exp 파싱 실패 — 재발급 진행"; exp_sec=0; }

    local now_sec
    now_sec=$(date +%s)
    local remaining=$(( exp_sec - now_sec ))

    echo "[tokens] 첫 번째 토큰 만료까지 ${remaining}s (필요: ${required_sec}s)"

    if (( remaining < required_sec )); then
        echo "[tokens] 만료 임박 — 전체 재발급 시작..."
        rm -f "$TOKENS_FILE"
        TEMP_DIR=$(mktemp -d)

        seq 0 $(( total_batches - 1 )) | xargs -P "$BATCH_PARALLEL" -I{} bash -c 'fetch_bulk "$@"' _ {}

        python3 -c "
import json, os, sys
temp_dir = '${TEMP_DIR}'
total = ${total_batches}
vus   = ${VUS}
tokens = []
for i in range(total):
    with open(os.path.join(temp_dir, f'{i}.json')) as f:
        tokens.extend(json.load(f))
json.dump(tokens[:vus], sys.stdout)
" > "$TOKENS_FILE"
        rm -rf "$TEMP_DIR"
        echo "✔ 토큰 재발급 완료 ($(python3 -c "import json; print(len(json.load(open('$TOKENS_FILE'))))")개)"
    fi
}

# sustain 테스트 소요: ~17m30s = 1050s, 여유 300s 추가 → 1350s
check_and_refresh_tokens 1350

fi # sustain-direct skip block end

# ── k6 실행 ───────────────────────────────────────────────────
LOG_DIR="${SCRIPT_DIR}/logs"
mkdir -p "$LOG_DIR"

# ── heap-guard 생명주기 관리 ──────────────────────────────────
# 서버 JVM 힙 사용률 임계치 — 초과 시 서버와 k6를 함께 종료한다.
# 변경 방법: HEAP_THRESHOLD=85 ./run.sh sustain
HEAP_THRESHOLD="${HEAP_THRESHOLD:-90}"

# k6 Go 런타임 메모리 제어
# GOGC=50 → GC 빈도 2배, 메모리 점유 감소 (CPU 소폭 증가)
# GOMEMLIMIT → OOM killer 방지 상한 (VU당 ~50KB 기준: 20000VU ≈ 1GB, 여유 포함 4GiB)
export GOGC="${GOGC:-50}"
export GOMEMLIMIT="${GOMEMLIMIT:-4GiB}"

start_heap_guard() {
    local k6_pid="$1"
    K6_PID="$k6_pid" THRESHOLD="$HEAP_THRESHOLD" "$SCRIPT_DIR/heap-guard.sh" &
    HEAP_GUARD_PID=$!
}

run_k6() {
    local script="$1"
    shift
    local log_file="${LOG_DIR}/$(basename "$script" .js)_$(date +%Y%m%d_%H%M%S).log"
    local tokens_arg=""
    [[ -f "$TOKENS_FILE" ]] && tokens_arg="-e K6_TOKENS_FILE=$TOKENS_FILE"
    echo "▶ [k6] $script 실행 중... (로그: $log_file)"
    k6 run \
        --compatibility-mode=base \
        --no-usage-report \
        -e BASE_URL="$BASE_URL" \
        -e MEMBER_URL="$MEMBER_URL" \
        -e NOTIFICATION_URL="http://localhost:8081" \
        -e K6_SEED_OFFSET="$SEED_OFFSET" \
        -e MAX_VUS="$VUS" \
        ${tokens_arg} \
        "$@" \
        "$SCRIPT_DIR/$script" 2>&1 | tee "$log_file" &
    local k6_pid=$!

    start_heap_guard "$k6_pid"
    wait "$k6_pid"
    stop_heap_guard

    echo "✔ $script 완료"
    echo ""
}

case "$TARGET" in
    connect)
        run_k6 connect.js --log-output=none
        ;;
    sustain)
        run_k6 sustain.js -e MAX_VUS="$SUSTAIN_MAX_VUS" -e START_VUS="$SUSTAIN_START_VUS" --log-output=none
        ;;
    sustain-direct)
        run_k6 sustain-direct.js -e MAX_VUS="$SUSTAIN_MAX_VUS" -e START_VUS="$SUSTAIN_START_VUS"
        ;;
    send)
        run_k6 send.js
        ;;
    receive)
        run_k6 receive.js --log-output=none
        ;;
    all)
        run_k6 all.js --log-output=none
        ;;
    prometheus-verify)
        PROMETHEUS_URL="${PROMETHEUS_URL:-http://localhost:9090}"
        run_k6 prometheus-verify.js \
            -e PROMETHEUS_URL="$PROMETHEUS_URL"
        ;;
    sse-limit)
        LOG_FILE="${LOG_DIR}/sse-limit_$(date +%Y%m%d_%H%M%S).log"
        if command -v gtimeout &>/dev/null; then
            TIMEOUT_BIN="gtimeout 30m"
        elif command -v timeout &>/dev/null; then
            TIMEOUT_BIN="timeout 30m"
        else
            TIMEOUT_BIN=""
        fi
        $TIMEOUT_BIN k6 run \
            -e BASE_URL="$BASE_URL" \
            -e K6_SEED_OFFSET="$SEED_OFFSET" \
            -e K6_TOKENS_FILE="$TOKENS_FILE" \
            -e MAX_VUS="$VUS" \
            --log-output=none \
            "$SCRIPT_DIR/sse-limit.js" 2>&1 | tee "$LOG_FILE" &
        K6_SSE_LIMIT_PID=$!
        start_heap_guard "$K6_SSE_LIMIT_PID"
        wait "$K6_SSE_LIMIT_PID"
        stop_heap_guard
        ;;
    *)
        echo "사용법: $0 [connect|send|receive|sustain|sustain-direct|sse-v2-poc|all|sse-limit|prometheus-verify] [VU수] [--noCleanUp]"
        exit 1
        ;;
esac
