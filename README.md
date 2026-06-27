# Haagendazs Backend

Spring Boot 4.0 기반 MSA 백엔드 프로젝트

## 기술 스택

| 분류 | 기술 |
|------|------|
| Language | Java 25 |
| Framework | Spring Boot 4.0.3 |
| Cloud | Spring Cloud 2025.1.x |
| Database | PostgreSQL 18 |
| Cache / Pub-Sub | Redis 8 |
| Message Broker | Apache Kafka 4.0 (KRaft) |
| Search | Elasticsearch 8.13 |
| Service Discovery | Eureka (Spring Cloud Netflix) |
| API Gateway | Spring Cloud Gateway |
| Build | Gradle (Multi-module) |

## 모듈 구성

| 모듈 | 포트 | 설명 |
|------|------|------|
| `config-server` | 8888 | 중앙 설정 서버 |
| `discovery` | 8761 | Eureka 서비스 레지스트리 |
| `gateway` | 8080 | API Gateway (JWT 검증, 라우팅) |
| `member` | 8084 | 회원 서비스 |
| `payment` | 8083 | 결제 서비스 |
| `search` | 8082 | 검색 서비스 (Elasticsearch) |
| `notification` | 8081 | 알림 서비스 (SSE, Redis Streams, Kafka) |
| `chat` | 8085 | 채팅 서비스 (R2DBC, Redis) |
| `common` | — | 공통 예외·응답 모듈 |

> `member`, `payment`, `search`, `notification`, `chat`은 스케일아웃 가능 (`container_name`, `ports` 미사용)

## 로컬 실행

### 사전 요구사항

- Docker Desktop
- `.env` 파일 (아래 항목 필요)

```env
POSTGRES_USER=
POSTGRES_PASSWORD=
REDIS_PASSWORD=
JWT_SECRET=
GITHUB_CONFIG_SERVER=
FRONT_URI=http://localhost:3000
```

### docker.sh 사용법

```bash
./docker.sh up                        # 전체 스택 기동
./docker.sh down                      # 전체 스택 종료
./docker.sh <service>                 # 특정 서비스 + 의존 인프라 기동
./docker.sh restart <service>         # 서비스 재빌드 후 재기동
./docker.sh scale <service> <n>       # 서비스 스케일 (예: ./docker.sh scale member 2)
./docker.sh ps                        # 컨테이너 상태 확인
./docker.sh logs [service]            # 로그 확인
```

**예시 — 알림 서비스만 실행**

```bash
./docker.sh notification
```

의존 인프라(config-server, discovery, postgres, redis, kafka)를 자동으로 먼저 기동합니다.

**예시 — 스케일아웃**

```bash
./docker.sh scale member 2   # member 인스턴스 2개로 확장
./docker.sh scale member 1   # 가장 오래된 컨테이너 순으로 축소
```

### 모니터링 포함 실행

```bash
docker compose -f docker-compose.local.yml -f docker-compose.monitoring.local.yml up -d
```

| 도구 | 주소 |
|------|------|
| Eureka Dashboard | http://localhost:8761 |
| Grafana | http://localhost:3000 |
| Prometheus | http://localhost:9090 |

## 브랜치 전략

```
main ← release ← develop ← feat/*
                          ← refactor/*
                          ← hotfix/*
```

| 브랜치 | 용도 |
|--------|------|
| `main` | 라이브 배포 |
| `release` | 배포 준비 |
| `develop` | 통합 개발 |
| `feat/*` | 기능 개발 |
| `refactor/*` | 리팩토링 |
| `hotfix/*` | 긴급 버그 수정 |

## CI/CD

PR을 `develop`으로 올리면 자동 실행됩니다.

- **변경된 모듈만 테스트** — `common` 변경 시 전체 모듈 테스트
- **모듈별 병렬 실행** — GitHub Actions matrix strategy
- **라벨 자동 관리** — 실제 수정한 모듈 라벨만 부착/제거
- **Assignee 자동 주입** — PR 작성자 자동 등록

## 아키텍처

```
Client
  │
  ▼
Gateway (8080) ── JWT 검증
  │
  ├── member       (8084) ── PostgreSQL ── Kafka
  ├── payment      (8083) ── PostgreSQL ── Kafka
  ├── search       (8082) ── PostgreSQL ── Elasticsearch ── Kafka
  ├── notification (8081) ── PostgreSQL ── Redis (Streams + Pub/Sub) ── Kafka
  └── chat         (8085) ── PostgreSQL ── Redis ── Kafka

인프라 기동 순서: config-server → discovery → [서비스]
```

**SSE 멀티 인스턴스:** 알림 서비스는 Redis Pub/Sub으로 모든 인스턴스에 브로드캐스트하여 스케일아웃 시에도 SSE 세션을 정확히 전달합니다.

## Config Server

설정은 중앙 config repo에서 관리합니다.
로컬에서는 `optional:configserver:` 설정으로 config repo 없이도 각 서비스의 `application.yml`로 동작합니다.
