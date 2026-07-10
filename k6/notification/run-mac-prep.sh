#!/usr/bin/env bash
# k6/notification/run-mac-prep.sh
# Mac에서 실행: DB seed 삽입 / 정리
# 토큰 발급은 k6 setup()이 담당하므로 이 스크립트에서 제외
#
# 사용법:
#   ./k6/notification/run-mac-prep.sh           # 기본 3000 VU seed 삽입
#   ./k6/notification/run-mac-prep.sh 1000      # VU 수 지정
#   ./k6/notification/run-mac-prep.sh --cleanup # seed 데이터 정리
#   ./k6/notification/run-mac-prep.sh 1000 --cleanup

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

DB_CONTAINER="${DB_CONTAINER:-sportsify-postgres}"
DB_NAME="${DB_NAME:-sportsify}"
DB_USER="${DB_USER:-sportsify}"

VUS=3000
CLEANUP=false
for arg in "$@"; do
    [[ "$arg" =~ ^[0-9]+$ ]] && VUS="$arg"
    [[ "$arg" == "--cleanup" ]] && CLEANUP=true
done

SEED_OFFSET=10000

psql_exec() {
    docker exec -i "$DB_CONTAINER" psql -U "$DB_USER" -d "$DB_NAME" "$@"
}

if [[ "$CLEANUP" == true ]]; then
    echo "▶ [cleanup] seed_cleanup.sql 실행 중... (VUS=$VUS, SEED_OFFSET=$SEED_OFFSET)"
    psql_exec -v vus="$VUS" -v seed_offset="$SEED_OFFSET" < "$SCRIPT_DIR/seed_cleanup.sql"
    echo "✔ cleanup 완료"
    exit 0
fi

echo "▶ [seed] seed.sql 실행 중... (VUS=$VUS, SEED_OFFSET=$SEED_OFFSET)"
psql_exec -v vus="$VUS" -v seed_offset="$SEED_OFFSET" < "$SCRIPT_DIR/seed.sql"
echo "✔ seed 완료"
echo ""
echo "  다음 단계: Windows PC에서 run-windows.ps1 실행"
echo "  테스트 후: ./k6/notification/run-mac-prep.sh $VUS --cleanup"
