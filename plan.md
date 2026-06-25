# Haagendazs Backend — 프로젝트 계획서

## 팀 멤버 & 도메인 담당

| 이름   | 담당 도메인         | 모듈명              | 상태     |
|--------|---------------------|---------------------|----------|
| 강정훈 | 알림 (Notification) | `notification`      | 확정     |
| 김민준 | 검색 (Search)       | `search`            | 확정     |
| 김준영 | 결제 / 회원         | `payment` or `member` | **미정 — 결정 필요** |
| 고정국 | 미정                | —                   | **결정 필요** |
| 강상욱 | 미정                | —                   | **결정 필요** |

> 각 멤버는 **본인 모듈만 코드 수정 권한**을 가진다.  
> 공통(common, gateway) 모듈 변경은 팀 전체 합의 후 진행한다.

---

## 기술 스택

| 구분            | 선택                               | 비고                                              |
|-----------------|------------------------------------|---------------------------------------------------|
| 언어            | Java 25                            | Spring Boot 4.x 런타임 Java 17+ 지원, Java 26 호환 확인됨 |
| 프레임워크      | Spring Boot 4.0.3                  | Jakarta EE 기반                                   |
| ORM             | Spring Data JPA + Hibernate 7.x    | `ddl-auto: validate`                              |
| 타입세이프 쿼리 | QueryDSL (OpenFeign fork 6.x)      | Spring Boot 3+/Jakarta EE 호환 버전               |
| 인증/인가       | Spring Security + OAuth2 (JWT)     |                                                   |
| 서비스 디스커버리 | Spring Cloud Netflix Eureka      | 서비스 레지스트리                                 |
| 중앙 설정 관리  | Spring Cloud Config Server         | Git 기반 설정 중앙화                              |
| 서블릿 컨테이너 | Tomcat (도메인 서비스)             | notification은 Netty(WebFlux) 사용                |
| 리액티브        | Netty (Spring WebFlux)             | gateway, notification 모듈                        |
| DB              | PostgreSQL                         |                                                   |
| 캐시            | Redis                              |                                                   |
| 검색엔진        | Elasticsearch                      | search 모듈 전용                                  |
| 스키마 관리     | Flyway                             | `V{버전}__{설명}.sql`                              |
| 모니터링        | Prometheus + Grafana               |                                                   |
| 컨테이너        | Docker / Docker Compose            | 로컬 개발환경                                     |
| 빌드            | Gradle                             | 멀티모듈                                          |
| 테스트          | JUnit 5, k6 (부하테스트)           |                                                   |

> **결정 필요 — Netty vs Tomcat 혼용 전략**  
> plan.md에 "netty, tomcat" 둘 다 명시되어 있다. 일반적인 패턴은 API Gateway만 WebFlux(Netty), 나머지 서비스는 MVC(Tomcat)으로 분리하는 것. 팀 합의 전까지 결정 보류.

- 알림, nettey 전략 사용
- 나머지는 선택적 기본값으로 tomcat설정
---

## 아키텍처 — DDD + MSA 멀티모듈

```
haagendazs-backend/
├── settings.gradle              ← 전체 모듈 등록
├── build.gradle                 ← 공통 BOM, 플러그인 버전 관리
│
├── config-server/               ← Spring Cloud Config Server (중앙 설정 관리)
│   └── src/main/java/...
│
├── discovery/                   ← Eureka Server (서비스 레지스트리)
│   └── src/main/java/...
│
├── gateway/                     ← API Gateway (Spring Cloud Gateway / WebFlux / Netty)
│   └── src/main/java/...        ← 라우팅, 인증 필터, Rate Limiting, Eureka 클라이언트
│
├── common/                      ← 공유 유틸, 예외, 도메인 이벤트 인터페이스
│   └── src/main/java/...
│
├── auth/                        ← OAuth2 Resource Server 공통 설정
│   └── src/main/java/...
│
├── notification/                ← 알림 (담당: 강정훈) — WebFlux/Netty
│   └── src/main/java/...
│
├── search/                      ← 검색 (담당: 김민준) — Tomcat
│   └── src/main/java/...
│
├── payment/                     ← 결제 (담당: 김준영, 도메인 미확정) — Tomcat
│   └── src/main/java/...
│
├── member/                      ← 회원 (담당: 미정) — Tomcat
│   └── src/main/java/...
│
└── [추가 도메인 모듈]            ← 고정국, 강상욱 도메인 확정 후 추가
```

### MSA 기본 인프라 모듈 상세

#### config-server
- Spring Cloud Config Server
- Git 레포(또는 로컬 파일)에서 각 서비스의 `application.yml`을 중앙 관리
- 서비스 기동 시 Config Server에서 설정을 받아옴 (`bootstrap.yml` or `spring.config.import`)
- 민감 정보(DB 비밀번호 등)는 환경변수로 주입, Config Server에는 키만 정의

#### discovery (Eureka Server)
- Spring Cloud Netflix Eureka Server
- 모든 서비스(gateway, notification, search, payment, member 등)가 Eureka Client로 등록
- gateway는 Eureka에서 서비스 목록을 조회해 동적 라우팅 (`lb://service-name`)
- 로컬 개발 시 단일 인스턴스, 운영 시 이중화 고려

#### gateway
- Spring Cloud Gateway (WebFlux 기반, Netty)
- Eureka 연동으로 로드밸런싱 라우팅
- JWT 검증 필터 (공통 auth 모듈 활용)
- Rate Limiting (Redis 기반)
- 각 도메인 서비스로 요청 프록시

#### 서비스 기동 순서
```
1. config-server 기동
2. discovery (Eureka) 기동
3. gateway 기동
4. 각 도메인 서비스 기동 (순서 무관)
```

### DDD 레이어 (각 도메인 모듈 내부)

```
{domain}/
└── src/main/java/com/haagendazs/{domain}/
    ├── domain/          ← Entity, VO, Repository 인터페이스, Domain Service
    ├── application/     ← Use Case (Command/Query), Application Service
    ├── infrastructure/  ← JPA Repository 구현체, 외부 API 클라이언트
    └── interfaces/      ← REST Controller, Request/Response DTO
```

---

## 모듈별 상세 계획

### common
- 공통 예외 클래스 (`BusinessException`, `ErrorCode`), 공통 예외 핸들러
- 공통 응답 포맷 (`ApiResponse<T>`)
- 도메인 이벤트 인터페이스 (서비스 간 이벤트 발행 계약)
- 공통 유틸 (날짜, 문자열 등)

### gateway
- Spring Cloud Gateway (WebFlux 기반, Netty)
- JWT 검증 필터
- 서비스별 라우팅 설정
- Rate Limiting (Redis 기반)

### auth
- Spring Security OAuth2 Resource Server 공통 설정
- JWT 발급 / 검증 유틸
- 각 서비스 모듈에서 의존하여 사용

### notification (담당: 강정훈)
- 이전 에 사용했던 코드 복붙
- redis, pub/sub

### search (담당: 김민준)
- **인프라**: Spring Data Elasticsearch

### payment (담당: 김준영 — 도메인 미확정)

### 미확정 모듈 (고정국, 강상욱)
- 도메인 확정 후 위 템플릿에 맞춰 추가

---

## 공통 인프라 컨벤션

### Spring Profile 전략

```
application.yml          → 공통 설정 (Jackson UTC, Flyway 활성화 등)
application-local.yml    → localhost DB/Redis, 목 메일
application-dev.yml      → 개발 서버, 환경변수 주입, DEBUG 로그
application-prod.yml     → 운영, 환경변수 주입, INFO 로그, management port 분리
```

### DB 스키마 관리 — Flyway

- 파일명: `src/main/resources/db/migration/V{버전}__{설명}.sql`
- 모든 환경(local/dev/prod/test)에서 Flyway가 스키마 책임
- JPA는 `ddl-auto: validate`만 — 스키마 변경은 반드시 마이그레이션 파일로
- 마이그레이션 파일은 한 번 커밋 후 **절대 수정 금지**

### 환경 변수 & 시크릿

- `.env` 파일 **절대 커밋 금지**
- `.gitignore`에 `.env*` 등록
- pre-commit hook으로 이중 차단
- 환경변수 키 목록은 `.env.example`에 값 없이 관리

### QueryDSL APT 설정 (Gradle)

```groovy
// build.gradle (각 도메인 모듈)
dependencies {
    implementation "io.github.openfeign.querydsl:querydsl-jpa:${querydslVersion}:jakarta"
    annotationProcessor "io.github.openfeign.querydsl:querydsl-apt:${querydslVersion}:jakarta"
    annotationProcessor "jakarta.persistence:jakarta.persistence-api"
}
```

---

## 테스트 전략

| 레벨          | 도구             | 대상                                |
|---------------|------------------|-------------------------------------|
| 단위 테스트   | JUnit 5          | Domain Service, Use Case, 유틸      |
| 통합 테스트   | JUnit 5 + Testcontainers | Repository, 외부 인프라 연동 |
| API 테스트    | JUnit 5 + MockMvc/WebTestClient | Controller 레이어 |
| 부하 테스트   | k6               | 핵심 API 엔드포인트                 |

> DB 테스트는 목(Mock) 금지 — 실제 DB(Testcontainers)로 검증

---

## 마일스톤 (초안 — 제작기간 총 1달)

| 단계 | 내용                                                        |
|------|-------------------------------------------------------------|
| 0    | 미확정 도메인 결정 (고정국, 강상욱, 김준영), Netty/Tomcat 전략 결정 |
| 1    | 공통 인프라 구성: 멀티모듈 Gradle 골격, Config Server, Eureka, Gateway, Docker Compose, Flyway, Auth |
| 2    | 각 도메인 모듈 Entity/Repository/UseCase 구현                |
| 3    | 도메인 간 연동 (이벤트, 알림 트리거 등)                      |
| 4    | 모니터링(Prometheus/Grafana) 연동, k6 부하테스트             |
| 5    | 코드 리뷰 & 통합 테스트, 배포 파이프라인 구성                |