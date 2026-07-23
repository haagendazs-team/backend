#!/usr/bin/env bash
set -euo pipefail

COMPOSE_FILE="docker-compose.local.yml"
SCALABLE_SERVICES="member payment search notification chat"

usage() {
    cat <<EOF
사용법:
  ./docker.sh up                        - 전체 스택 기동
  ./docker.sh down                      - 전체 스택 종료
  ./docker.sh <service>                 - 특정 서비스 + 의존 인프라 기동
  ./docker.sh restart <service>         - 서비스 재빌드 후 재기동
  ./docker.sh scale <service> <n>       - 서비스 스케일 (예: ./docker.sh scale member 2)
  ./docker.sh ps                        - 컨테이너 상태 확인
  ./docker.sh logs [service]            - 서비스 로그 확인

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

cmd_restart() {
    local service="${1:-}"
    if [[ -z "$service" ]]; then
        echo "오류: 서비스명을 입력하세요." >&2
        exit 1
    fi

    echo "[restart] $service 재빌드 및 재기동..."
    docker compose -f "$COMPOSE_FILE" up -d --build --no-deps "$service"
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

    local current
    current=$(docker compose -f "$COMPOSE_FILE" ps -q "$service" 2>/dev/null | wc -l | tr -d ' ')

    if [[ "$count" -lt "$current" ]]; then
        local remove_count=$(( current - count ))
        echo "[scale] $service $current → $count (오래된 컨테이너 $remove_count개 종료)"
        docker compose -f "$COMPOSE_FILE" ps -q "$service" \
            | xargs -I{} docker inspect --format '{{.Id}} {{.State.StartedAt}}' {} \
            | sort -k2 \
            | head -n "$remove_count" \
            | awk '{print $1}' \
            | xargs -r docker stop \
            | xargs -r docker rm
    else
        echo "[scale] $service x $count 기동..."
        docker compose -f "$COMPOSE_FILE" up -d --scale "$service=$count" --no-recreate "$service"
        echo "[scale] Eureka 등록 확인: http://localhost:8761"
    fi
}

cmd_service() {
    local service="${1:-}"
    if [[ -z "$service" ]]; then
        echo "오류: 서비스명을 입력하세요." >&2
        exit 1
    fi

    local deps="config-server discovery"
    case "$service" in
        member|payment|search)
            deps="$deps postgres kafka" ;;
        notification)
            deps="$deps postgres redis kafka" ;;
        chat)
            deps="$deps postgres redis kafka" ;;
        gateway)
            deps="$deps redis" ;;
        *)
            echo "오류: 알 수 없는 서비스 '$service'" >&2
            exit 1 ;;
    esac

    echo "[build] $service gradle 빌드 중..."
    ./gradlew ":${service}:build" -x test --quiet || { echo "오류: gradle 빌드 실패" >&2; exit 1; }

    echo "[service] $service 및 의존 인프라 기동: $deps"
    docker compose -f "$COMPOSE_FILE" up -d --no-recreate $deps
    docker compose -f "$COMPOSE_FILE" up -d --build --force-recreate --no-deps "$service"
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
    up)      cmd_up ;;
    down)    cmd_down ;;
    restart) cmd_restart "${1:-}" ;;
    scale)   cmd_scale "${1:-}" "${2:-}" ;;
    ps)      cmd_ps ;;
    logs)    cmd_logs "${1:-}" ;;
    *)
        if is_scalable "$COMMAND" || [[ "$COMMAND" == "gateway" ]]; then
            cmd_service "$COMMAND"
        else
            usage; exit 1
        fi ;;
esac
