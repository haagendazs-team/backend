#!/usr/bin/env bash
set -euo pipefail

COMPOSE_FILE="docker-compose.local.yml"
SCALABLE_SERVICES="member payment search notification chat"

usage() {
    cat <<EOF
사용법:
  ./docker.sh up                        - 전체 스택 기동
  ./docker.sh down                      - 전체 스택 종료
  ./docker.sh scale <service> <n>       - 서비스 스케일 (예: ./docker.sh scale member 2)
  ./docker.sh ps                        - 컨테이너 상태 확인
  ./docker.sh logs <service>            - 서비스 로그 확인

스케일 가능한 서비스: $SCALABLE_SERVICES
EOF
}

is_scalable() {
    local service="$1"
    for s in $SCALABLE_SERVICES; do
        if [[ "$s" == "$service" ]]; then
            return 0
        fi
    done
    return 1
}

cmd_up() {
    echo "[up] 전체 스택 기동..."
    docker compose -f "$COMPOSE_FILE" up -d
}

cmd_down() {
    echo "[down] 전체 스택 종료..."
    docker compose -f "$COMPOSE_FILE" down
}

cmd_scale() {
    local service="${1:-}"
    local count="${2:-}"

    if [[ -z "$service" || -z "$count" ]]; then
        echo "오류: 서비스명과 개수를 입력하세요." >&2
        echo "예: ./docker.sh scale member 2" >&2
        exit 1
    fi

    if ! is_scalable "$service"; then
        echo "오류: '$service'는 스케일 불가 서비스입니다." >&2
        echo "스케일 가능: $SCALABLE_SERVICES" >&2
        exit 1
    fi

    if ! [[ "$count" =~ ^[1-9][0-9]*$ ]]; then
        echo "오류: 개수는 1 이상의 정수여야 합니다." >&2
        exit 1
    fi

    echo "[scale] $service x $count 기동..."
    docker compose -f "$COMPOSE_FILE" up -d --scale "$service=$count" --no-recreate "$service"
    echo "[scale] Eureka 등록 확인: http://localhost:8761"
}

cmd_ps() {
    docker compose -f "$COMPOSE_FILE" ps
}

cmd_logs() {
    local service="${1:-}"
    if [[ -z "$service" ]]; then
        docker compose -f "$COMPOSE_FILE" logs -f --tail=50
    else
        docker compose -f "$COMPOSE_FILE" logs -f --tail=50 "$service"
    fi
}

COMMAND="${1:-}"
shift || true

case "$COMMAND" in
    up)     cmd_up ;;
    down)   cmd_down ;;
    scale)  cmd_scale "${1:-}" "${2:-}" ;;
    ps)     cmd_ps ;;
    logs)   cmd_logs "${1:-}" ;;
    *)      usage; exit 1 ;;
esac
