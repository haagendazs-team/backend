# Haagendazs Backend

Spring Boot 4.0.3 기반 MSA 백엔드 프로젝트

## 기술 스택

| 분류 | 기술 |
|------|------|
| Language | Java 25 |
| Framework | Spring Boot 4.0.3 |
| Cloud | Spring Cloud 2025.1.x (Boot 4.x 전용) |
| Database | PostgreSQL 18 |
| Cache | Redis 8 |
| Search | Elasticsearch 8.13 |
| Build | Gradle |

## 서비스 포트

| 서비스 | 포트 |
|--------|------|
| Gateway | 8080 |
| Notification | 8081 |
| Search | 8082 |
| Payment | 8083 |
| Member | 8084 |
| Eureka (Discovery) | 8761 |
| Config Server | 8888 |
| PostgreSQL | 5432 |
| Redis | 6379 |
| Elasticsearch | 9200 |
| Prometheus | 9090 |
| Grafana | 3001 |

## 로컬 개발 시작

### 1. 사전 요구사항

- Java 25
- Docker Desktop
- Gradle (또는 `./gradlew` 사용)

### 2. 환경 변수 설정

```bash
cp .env.example .env
```

`.env` 파일을 열어 빈 값을 채웁니다.

```env
POSTGRES_USER=haagendazs
POSTGRES_PASSWORD=패스워드설정
REDIS_PASSWORD=패스워드설정
JWT_SECRET=32자리이상랜덤문자열
```

### 3. 인프라 실행

```bash
# 인프라만 (PostgreSQL, Redis, Elasticsearch)
docker compose -f docker-compose.local.yml up -d

# 모니터링 포함 (Prometheus, Grafana)
docker compose -f docker-compose.local.yml -f docker-compose.monitoring.yml up -d
```

### 4. 서비스 빌드

```bash
./gradlew bootJar
```

### 5. 서비스 실행 순서

인프라가 healthy 상태가 된 후 아래 순서로 실행합니다.

```bash
# 1) Config Server
java -jar config-server/build/libs/config-server-0.0.1-SNAPSHOT.jar

# 2) Discovery (Eureka)
java -jar discovery/build/libs/discovery-0.0.1-SNAPSHOT.jar

# 3) Gateway
java -jar gateway/build/libs/gateway-0.0.1-SNAPSHOT.jar

# 4) 도메인 서비스 (순서 무관)
java -jar member/build/libs/member-0.0.1-SNAPSHOT.jar
java -jar payment/build/libs/payment-0.0.1-SNAPSHOT.jar
java -jar search/build/libs/search-0.0.1-SNAPSHOT.jar
java -jar notification/build/libs/notification-0.0.1-SNAPSHOT.jar
```

> 환경 변수는 실행 전 `export $(grep -v '^#' .env | xargs)` 또는 IDE Run Configuration에 `.env` 파일을 등록하여 주입합니다.

### 6. 동작 확인

```bash
# Eureka 등록 현황
curl http://localhost:8761

# Gateway 헬스체크
curl http://localhost:8080/actuator/health

# 서비스별 라우팅 확인
curl http://localhost:8080/api/members/actuator/health
curl http://localhost:8080/api/payments/actuator/health
curl http://localhost:8080/api/notifications/actuator/health
curl http://localhost:8080/api/search/actuator/health
```

## 브랜치 전략

```
main ← release ← develop ← feat/*
```

| 브랜치 | 용도 |
|--------|------|
| `main` | 라이브 배포 |
| `release` | 배포 준비 |
| `develop` | 통합 개발 |
| `feat/*` | 기능 개발 |
| `hotfix/*` | 긴급 버그 수정 |

## Config Server

설정은 [config_repo](https://github.com/haagendazs-team/config_repo)에서 중앙 관리합니다.  
로컬에서는 `optional:configserver:` 설정으로 repo가 비어있어도 각 서비스의 `application.yml`로 동작합니다.
