# Notification Service PRD

## 1. 개요

회원에게 이벤트 기반 알림을 실시간(SSE) 및 채널(Email 등)로 전달하는 독립 마이크로서비스.
이벤트 타입을 런타임에 동적으로 등록할 수 있으며, 수평 확장과 장애 복구를 자체적으로 처리한다.

---

## 2. 핵심 기능

### 2-1. 알림 인박스

| 기능 | 설명 |
|------|------|
| 알림 목록 조회 | offset/limit 기반 페이징, 최신순 정렬 |
| 단건 읽음 처리 | 이미 읽은 알림 재요청 시 오류 반환 |
| 전체 읽음 처리 | 해당 회원의 미읽음 알림 일괄 처리 |
| 실시간 SSE 구독 | 연결 시 알림 스트림 수신, 연결 해제 시 자동 정리 |

### 2-2. 알림 설정

- 이벤트 타입별 ON/OFF 개인 설정 (`setting_entries`)
- 설정이 없는 이벤트 타입은 기본값 활성화로 처리
- 이벤트 타입 동적 추가 시 기존 회원 설정 자동 적용 (기본 활성화)

### 2-3. 발송 채널

- **SSE**: 앱 접속 중 실시간 전달
- **Email**: 채널 등록 시 병렬 발송 (비동기, SSE와 독립)
- 채널은 회원당 최대 2개 등록 가능
- 채널 타입별 중복 등록 불가

### 2-4. 이벤트 타입 동적 등록

코드 배포 없이 새 이벤트 타입을 런타임에 등록한다.

| 속성 | 설명 |
|------|------|
| `code` | 이벤트 고유 코드 (예: `TICKET_OPEN`) |
| `streamKey` | Redis Stream 키 (예: `notif:stream:ticket.opened`) |
| `isScheduled` | 예약 알림 여부 |
| `isSingleTarget` | 특정 회원 단독 발송 여부 (false = 전체 브로드캐스트) |
| `memberIdField` | payload에서 memberId 추출할 필드명 |
| `scheduledAtField` | payload에서 예약 시각 추출할 필드명 |
| `scheduledOffsetMinutes` | 예약 시각 기준 offset (분 단위, 예: 30 → 30분 전 발송) |

**기본 등록 이벤트 타입:**

| code | 대상 | 예약 |
|------|------|------|
| `TICKET_OPEN` | 전체 브로드캐스트 | O (판매 시작 시각) |
| `GAME_START` | 전체 브로드캐스트 | O (경기 시작 30분 전) |
| `PAYMENT_COMPLETED` | 단일 회원 | X |
| `CHAT_MENTION` | 단일 회원 | X |
| `CHAT_INVITED` | 단일 회원 | X |

---

## 3. 이벤트 처리 흐름

### 3-1. 이벤트 유입 경로

```
타 서비스 (payment, chat 등)
  └─ Kafka 토픽 발행 (member.joined / payment.completed 등)
        └─ KafkaNotificationConsumer 수신
              └─ Redis Stream으로 포워딩

타 서비스 (내부 모듈 API 호출)
  └─ POST /module/notifications/publish
        └─ Redis Stream에 직접 적재
```

### 3-2. Redis Stream 소비 흐름

```
Redis Stream
  └─ StreamSubscriptionManager (Consumer Group)
        ├─ 즉시 발송 이벤트 → FanoutService → BufferedChunkService
        │     ├─ SSE 연결됨 → NotificationBatchBuffer (메모리 큐)
        │     │     └─ flush() → DB 저장 + SSE 전송 + 채널 발송
        │     └─ SSE 연결 없음 → BulkPersistService (즉시 DB 저장 + 채널 발송)
        │
        └─ 예약 이벤트 → DB 저장 (PENDING) → ACK
                              └─ ScheduledNotificationProcessor (5분 주기)
                                    → 발송 시각 도래 시 FanoutService → ChunkService
```

### 3-3. 이벤트 상태 전이

```
PENDING → PROCESSING → PUBLISHED
                    ↘ FAILED → (retry) → PERMANENTLY_FAILED
                                       → CANCELLED
```

| 상태 | 설명 |
|------|------|
| `PENDING` | 발송 대기 (예약 포함) |
| `PROCESSING` | 처리 중 |
| `PUBLISHED` | 발송 완료 |
| `FAILED` | 발송 실패, 재시도 대기 |
| `PERMANENTLY_FAILED` | 재시도 소진, 영구 실패 |
| `CANCELLED` | 취소됨 |

---

## 4. 장애 복구 전략

### 4-1. PEL (Pending Entry List) 재처리

Redis Stream Consumer Group에서 ACK되지 않은 메시지(PEL)를 주기적으로 재처리한다.

- **재처리 주기**: 설정값(`pelReclaimCron`)에 따른 스케줄 실행
- **backoff 전략**: 재시도 횟수에 따라 대기 시간 점진적 증가 (`backoffMinutes` 목록)
- **재시도 소진 시**: `PERMANENTLY_FAILED` 처리 + `NotificationPermanentlyFailedEvent` 발행

### 4-2. Stuck 이벤트 복구

`PROCESSING` 상태에서 `stuckTimeout` 이상 경과한 이벤트를 `PENDING`으로 복구한다.

- **감지**: `ScheduledTriggerPublisher`가 주기적으로 Redis Pub/Sub으로 트리거 발행
- **복구**: `ScheduledTriggerSubscriber` 수신 → `claimStuckEvents()` → `PENDING` 복구
- **무한루프 방지**: `stuck_retry_count >= maxStuckRetry` 도달 시 복구 중단

### 4-3. 수평 확장 시 중복 처리 방지

- Redis Stream Consumer Group으로 인스턴스 간 메시지 분산
- `notifications` 테이블의 `UNIQUE(event_id, member_id)` 제약으로 중복 적재 차단
- 예약 이벤트 클레임은 분산 락(`leaderLockTtl`)으로 단일 인스턴스만 처리

---

## 5. API

### 클라이언트 API (`/api/notifications`)

| 메서드 | 경로 | 설명 |
|--------|------|------|
| GET | `/api/notifications` | 알림 목록 조회 (offset, limit) |
| PATCH | `/api/notifications/{id}/read` | 단건 읽음 |
| PATCH | `/api/notifications/read-all` | 전체 읽음 |
| GET | `/api/notifications/stream` | SSE 구독 |
| GET | `/api/notifications/settings` | 알림 설정 목록 조회 |
| PUT | `/api/notifications/settings` | 알림 설정 변경 |
| GET | `/api/notifications/channels` | 채널 목록 조회 |
| POST | `/api/notifications/channels` | 채널 등록 |
| DELETE | `/api/notifications/channels/{id}` | 채널 삭제 |
| PATCH | `/api/notifications/channels/{id}/toggle` | 채널 활성/비활성 토글 |

> 모든 클라이언트 API는 `X-Member-Id` 헤더로 회원 식별 (Gateway에서 주입)

### 내부 모듈 API (`/module/notifications`)

| 메서드 | 경로 | 설명 |
|--------|------|------|
| POST | `/module/notifications/publish` | 단건 이벤트 발행 |
| POST | `/module/notifications/publish-batch` | 다건 이벤트 일괄 발행 |
| POST | `/module/notifications/event-types` | 이벤트 타입 동적 등록 |

> 내부 API는 타 서비스가 직접 호출, Gateway 인증 미적용

---

## 6. 오류 코드

| 코드 | HTTP | 설명 |
|------|------|------|
| N001 | 404 | 알림을 찾을 수 없음 |
| N002 | 400 | 이미 읽은 알림 |
| N003 | 404 | 알림 설정을 찾을 수 없음 |
| N004 | 404 | 알림 채널을 찾을 수 없음 |
| N005 | 409 | 이미 등록된 채널 |
| N006 | 400 | 채널 등록 한도 초과 (최대 2개) |
| N007 | 400 | 지원하지 않는 채널 타입 |
| N008 | 500 | 알림 발송 실패 |
| N009 | 404 | 존재하지 않는 이벤트 타입 |
| N010 | 409 | 이미 등록된 이벤트 타입 |
| N011 | 400 | 유효하지 않은 stream key 형식 |

---

## 7. 기술 스택

| 항목 | 기술 |
|------|------|
| 런타임 | Spring WebFlux (R2DBC, Reactive) |
| DB | PostgreSQL (`notification` 스키마) |
| 메시징 | Redis Stream (Consumer Group) |
| 실시간 전달 | SSE + Redis Pub/Sub (멀티 인스턴스 브로드캐스트) |
| 이벤트 유입 | Apache Kafka |
| 스키마 관리 | Flyway |

---

## 8. 제약 조건

- 채널은 회원당 최대 2개
- stream key 형식: `notif:stream:{name}` 패턴
- `V*.sql` 마이그레이션 파일은 추가만 가능, 수정 금지
- 타 서비스 DB에 직접 접근하지 않음 (member_id는 이벤트 payload에서 추출)
- `PERMANENTLY_FAILED` 단일 대상 이벤트는 알림함에 기록 보존
