#!/usr/bin/env bash
# k6 member 도메인 부하 테스트
#
# 사용법:
#   ./k6/member/run.sh                    # mixed 시나리오 (기본 50 VU)
#   ./k6/member/run.sh login              # 로그인 처리량
#   ./k6/member/run.sh signup             # 회원가입 처리량
#   ./k6/member/run.sh profile            # /members/me 조회·수정
#   ./k6/member/run.sh workspace          # workspace CRUD
#   ./k6/member/run.sh channel            # channel CRUD
#   ./k6/member/run.sh mixed 100          # VU 수 지정
#   ./k6/member/run.sh login --noCleanUp  # 시드 유지
#
# 환경 변수 (선택):
#   BASE_URL      — Gateway 경유 URL (기본: http://localhost:8080/api/members)
#   MEMBER_URL    — member 직접 URL (BASE_URL 미설정 시 helper fallback)
#   DB_CONTAINER  — PostgreSQL 컨테이너 (기본: haagendazs-postgres)
#   DB_NAME       — DB 이름 (기본: haagendazs)
#   DB_USER       — DB 사용자 (기본: haagendazs / POSTGRES_USER)
#
# 사전 조건:
#   1) member(+gateway) 기동
#   2) 모니터링: docker compose -f docker-compose.local.yml -f docker-compose.monitoring.local.yml up -d
#   3) Grafana: http://localhost:3001 (Member 대시보드)

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

BASE_URL="${BASE_URL:-http://localhost:8080/api/members}"
DB_CONTAINER="${DB_CONTAINER:-haagendazs-postgres}"
DB_NAME="${DB_NAME:-haagendazs}"
DB_USER="${DB_USER:-${POSTGRES_USER:-haagendazs}}"

psql_exec() {
    docker exec -i "$DB_CONTAINER" psql -U "$DB_USER" -d "$DB_NAME" "$@"
}

TARGET="${1:-mixed}"
NO_CLEANUP=false
_USER_VUS=""
for arg in "$@"; do
    [[ "$arg" == "--noCleanUp" ]] && NO_CLEANUP=true
    [[ "$arg" =~ ^[0-9]+$ ]]     && _USER_VUS="$arg"
done

case "$TARGET" in
    signup) DEFAULT_VUS=30 ;;
    workspace|channel) DEFAULT_VUS=30 ;;
    *) DEFAULT_VUS=50 ;;
esac
VUS="${_USER_VUS:-$DEFAULT_VUS}"
SEED_OFFSET="${SEED_OFFSET:-10000}"

# signup 은 DB seed 불필요 (unique email 생성)
NEEDS_SEED=true
[[ "$TARGET" == "signup" ]] && NEEDS_SEED=false

cleanup() {
    if [[ "$NEEDS_SEED" != true ]]; then
        # signup 잔여 계정만 정리
        if [[ "$NO_CLEANUP" == true ]]; then
            echo "[cleanup 생략] k6-signup-* 계정이 DB에 남아 있을 수 있습니다."
            return
        fi
        echo ""
        echo "▶ [cleanup] signup 잔여 데이터 정리..."
        psql_exec < "$SCRIPT_DIR/seed_cleanup.sql" \
            && echo "✔ cleanup 완료" \
            || echo "✘ cleanup 실패 (수동 확인 필요)"
        return
    fi

    if [[ "$NO_CLEANUP" == true ]]; then
        echo ""
        echo "[cleanup 생략] seed 데이터가 DB에 남아 있습니다."
        echo "  수동 정리: docker exec -i $DB_CONTAINER psql -U $DB_USER -d $DB_NAME -v vus=$VUS -v seed_offset=$SEED_OFFSET -f - < k6/member/seed_cleanup.sql"
        return
    fi
    echo ""
    echo "▶ [cleanup] seed_cleanup.sql 실행 중..."
    psql_exec -v vus="$VUS" -v seed_offset="$SEED_OFFSET" < "$SCRIPT_DIR/seed_cleanup.sql" \
        && echo "✔ cleanup 완료" \
        || echo "✘ cleanup 실패 (수동 확인 필요)"
}
trap cleanup EXIT

if [[ "$NEEDS_SEED" == true ]]; then
    echo "▶ [seed] seed.sql 실행 중... (VUS=$VUS, SEED_OFFSET=$SEED_OFFSET)"
    psql_exec -v vus="$VUS" -v seed_offset="$SEED_OFFSET" < "$SCRIPT_DIR/seed.sql"
    echo "✔ seed 완료"
    echo ""
fi

LOG_DIR="${SCRIPT_DIR}/logs"
mkdir -p "$LOG_DIR"

run_k6() {
    local script="$1"
    shift
    local log_file="${LOG_DIR}/$(basename "$script" .js)_$(date +%Y%m%d_%H%M%S).log"
    echo "▶ [k6] $script 실행 중... (BASE_URL=$BASE_URL, VUS=$VUS)"
    echo "  로그: $log_file"
    k6 run \
        --compatibility-mode=base \
        --no-usage-report \
        -e BASE_URL="$BASE_URL" \
        -e K6_SEED_OFFSET="$SEED_OFFSET" \
        -e K6_SEED_PASSWORD="PerfTest1!" \
        -e MAX_VUS="$VUS" \
        -e K6_RUN_ID="$(date +%Y%m%d%H%M%S)" \
        "$@" \
        "$SCRIPT_DIR/$script" 2>&1 | tee "$log_file"
    echo "✔ $script 완료"
    echo ""
}

case "$TARGET" in
    login)     run_k6 login.js ;;
    signup)    run_k6 signup.js ;;
    profile)   run_k6 profile.js ;;
    workspace) run_k6 workspace.js ;;
    channel)   run_k6 channel.js ;;
    mixed)     run_k6 mixed.js ;;
    *)
        echo "사용법: $0 [login|signup|profile|workspace|channel|mixed] [VU수] [--noCleanUp]"
        exit 1
        ;;
esac
