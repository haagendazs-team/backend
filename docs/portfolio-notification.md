# Notification Service — 포트폴리오

> 실시간 이벤트 기반 알림 시스템 | 200 → **24,000 CCU (120배)** 달성

**기간**: 2026.06 ~ 2026.07  
**역할**: 백엔드 개발 (단독)  
**스택**: Java 25 · Spring Boot 4 · WebFlux · R2DBC · Redis Stream · Redis Pub/Sub · Kafka · PostgreSQL · Docker · Prometheus · Grafana

---

## 1. 배경 & 요구사항

스포츠 티켓 플랫폼에서 회원이 경기 시작, 티켓 오픈, 결제 완료 등의 이벤트를 실시간으로 받아야 했습니다.
초기 목표는 DAU 3,000명 / 피크 CCU 1,000명이었으나, 부하 테스트 과정에서 운영 목표의 4배인 24K CCU까지 검증하는 것으로 목표를 상향했습니다.

**핵심 제약 조건:**

| 요구사항 | 선택한 기술 | 이유 |
|---|---|---|
| 알림 유실 금지 | Redis Stream Consumer Group | ACK 기반 메시지 확인, PEL로 미처리 메시지 재처리 가능 |
| 실시간 전달 | SSE | WebSocket 대비 단방향 · 가볍고 HTTP/2 multiplexing 활용 가능 |
| 다중 인스턴스 지원 | Redis Pub/Sub | 브로드캐스트 메시지를 인스턴스 경계 없이 전달 |
| 예약 알림 지원 | DB PENDING + 스케줄러 | 발송 시각 이전 이벤트를 상태 관리로 보관 |
| 장애 발생 시 재처리 | PEL 재처리 + Exponential Backoff | ACK 미확인 메시지를 자동 재소비 |
| 수평 확장 | Consumer Group + 분산 락 | 인스턴스 간 중복 처리 방지 |

---

## 2. 시스템 아키텍처

```
타 서비스 (payment, chat 등)
  └─ Kafka 토픽 발행
        └─ KafkaNotificationConsumer
              └─ Redis Stream (단일 스트림, Consumer Group)
                    └─ StreamSubscriptionManager
                          ├─ 즉시 이벤트
                          │     └─ FanoutService
                          │           ├─ SSE 연결됨 → NotificationBatchBuffer (200건 / 500ms bulk insert + SSE 전송)
                          │           └─ SSE 미연결 → BulkPersistService (즉시 DB 저장)
                          │
                          └─ 예약 이벤트 → DB 저장(PENDING) → ACK
                                              └─ ScheduledNotificationProcessor (5분 주기)
                                                    └─ 발송 시각 도래 시 FanoutService

SSE 멀티 인스턴스 브로드캐스트:
  SseEmitterManager.send() → Redis Pub/Sub 발행
    └─ SseBroadcastSubscriber (각 인스턴스) → sendLocal()
```

**단일 스트림 설계 이유:** 초기에는 이벤트 타입별로 Redis Stream 키를 분리했으나, 이벤트 타입 동적 등록 시마다 Consumer Group을 새로 생성해야 했고 관리 복잡도가 급증했습니다. 단일 스트림 + `NotificationEnvelope`로 payload를 래핑하는 구조로 전환하여 StreamSubscriptionManager가 하나의 Consumer Group만 관리하도록 단순화했습니다.

**이벤트 상태 전이:**
```
PENDING → PROCESSING → PUBLISHED
                    ↘ FAILED → (retry) → PERMANENTLY_FAILED
```

---

## 3. 핵심 기능

### 3-1. 이벤트 타입 동적 등록
코드 배포 없이 런타임에 새 이벤트 타입을 등록합니다. 등록 즉시 Redis Stream 구독이 시작되며, 기존 회원의 알림 설정에 기본값(활성화)이 자동 적용됩니다.

기본 등록 이벤트 타입: `TICKET_OPEN` (전체 브로드캐스트 + 예약), `GAME_START` (전체 브로드캐스트 + 예약), `PAYMENT_COMPLETED` / `CHAT_MENTION` / `CHAT_INVITED` (단일 회원)

### 3-2. SSE 실시간 알림 + Replay
구독 시 `lastEventId` 기반으로 미수신 알림을 최대 200건 재전송합니다. 네트워크 단절 후 재연결 시 유실 없이 복구됩니다.

### 3-3. 알림 설정 캐시
이벤트 발생마다 DB를 조회하는 대신 `RedisSettingCacheAdapter`가 설정을 캐싱합니다. 팬아웃 대상이 수천 명일 때 DB 쿼리 수를 대폭 줄입니다.

### 3-4. 배치 버퍼 알림 저장
`NotificationBatchBuffer`: SSE 연결 중인 회원의 알림을 200건 또는 500ms 기준으로 모아서 bulk insert합니다. 건별 insert 대비 DB 커넥션 점유를 최소화하면서 실시간성을 유지합니다.

---

## 4. 기술적 도전 & 최적화

### 문제 인식

초기 부하 테스트에서 **200 CCU** 시점에 SSE 연결이 일제히 실패했습니다. 단순히 코드 버그가 아닌, 계층별 병목이 순차적으로 나타나는 문제였습니다.

최적화 로드맵:
```
200 → 6,000 → 10,500 → 16,400 → 24,000 CCU
```

---

### Phase 1 — OS 레벨 (200 → 6,000)

**문제:** 256 CCU 이후 `accept()` 실패

**원인 분석:**
```
macOS 기본 ulimit -n = 256
→ k6 / 앱 프로세스에서 256개 fd 소비 후 즉시 고갈
kern.ipc.somaxconn = 128
→ SYN 폭주 시 OS가 패킷 드롭
```

SSE는 장시간 유지되는 TCP 연결이므로 연결 수 = fd 소비 수입니다. OS 레벨 한계가 가장 먼저 충돌했습니다.

**해결:**
```bash
ulimit -n 131072
sudo sysctl -w kern.maxfiles=131072
sudo sysctl -w kern.maxfilesperproc=131072
sudo sysctl -w kern.ipc.somaxconn=65535
```

**결과:** 200 → **6,000 CCU**

---

### Phase 2 — JVM GC (6,000 → 10,500)

**문제:** 10K 연결 근처에서 전체 응답 수 초 단위로 멈춤

**원인 분석:**  
Heap Dump + GC Log로 확인한 결과, G1GC Major GC STW가 2~3초 발생했습니다.

```
SseSession × 10K + Sinks.many().multicast() 내부 버퍼 × 10K
→ ping 브로드캐스트 시 매 60초마다 ServerSentEvent 객체가 10K개 순간 생성
→ G1GC Major GC STW 2~3초 → Netty 이벤트 루프 stall → SSE 청크 전송 지연 → k6 타임아웃
```

**해결:**
```bash
JAVA_TOOL_OPTIONS=-XX:+UseZGC -Xmx1g -XX:SoftMaxHeapSize=800m \
  -Dio.netty.maxDirectMemory=1073741824
```

G1GC → ZGC 전환으로 STW를 2~3초에서 1ms 미만으로 감소시켰습니다. `SoftMaxHeapSize=800m`으로 GC 트리거 시점을 앞당겨 Full GC를 차단했습니다.

**결과:** 6,000 → **10,500 CCU**

---

### Phase 3 — 애플리케이션 구조 (10,500 → 16,400)

**문제 A: Thundering Herd**

SSE 세션의 기본 타임아웃이 고정값이어서 수천 세션이 동시에 만료 → 동시 재연결 폭풍 → GC 압박 → STW 반복.

**해결:**
```java
long jitterMs = ThreadLocalRandom.current().nextLong(-30_000, 30_000);
long sessionTimeoutMs = properties.sse().timeoutMs() + jitterMs;
return Flux.concat(replay, sink.asFlux()).timeout(Duration.ofMillis(sessionTimeoutMs));
```
세션 만료 시점을 ±30초 분산하여 재연결 폭풍을 차단했습니다.

**문제 B: DB 커넥션 풀 경합**

SSE 재연결 시 replay 조회 + 팬아웃 알림 저장이 동시에 DB 풀(max=20)을 경합했습니다.

**해결:** `NotificationBatchBuffer`로 SSE 연결 중인 회원의 알림을 200건 / 500ms 주기 bulk insert로 전환. DB 커넥션 점유 횟수를 대폭 줄였습니다.

**결과:** 10,500 → **16,400 CCU**

---

### Phase 4 — 컨테이너 런타임 (16,400 → 24,000)

**문제:** 16,400 이상에서 연결 수립 실패 재현

**원인 분석:**
```
macOS 기본 ephemeral 포트: 49,152~65,535 = 16,383 포트
→ k6 → localhost:8081 TCP 연결 시 클라이언트 포트 소진
Docker 기본 nofile = 1,024
→ 컨테이너 내부에서 호스트 OS 설정과 독립적으로 fd 고갈
```

**해결:**
```bash
# macOS ephemeral 포트 확장: 49,152 → 32,768 시작 (가용 32,767 포트)
sudo sysctl -w net.inet.ip.portrange.first=32768
```

```yaml
# docker-compose.local.yml
notification:
  mem_limit: 4g
  ulimits:
    nofile: { soft: 65535, hard: 65535 }
  sysctls:
    net.core.somaxconn: 65535
    net.ipv4.tcp_max_syn_backlog: 65535
    net.ipv4.ip_local_port_range: "32768 65535"
    net.ipv4.tcp_tw_reuse: 1
    net.ipv4.tcp_fin_timeout: 15
```

Docker 컨테이너는 Linux 네임스페이스 단위로 ulimit가 분리되므로, 호스트 OS 설정과 별개로 명시해야 한다는 점을 이번에 직접 확인했습니다.

**결과:** 16,400 → **24,000 CCU**

---

### 단계별 성과 요약

| Phase | 병목 계층 | 주요 개선 | 달성 CCU |
|---|---|---|---|
| 기준 | — | — | 200 |
| Phase 1 | OS 커널 | fd 한계 / TCP accept 큐 해제 | 6,000 |
| Phase 2 | JVM | G1GC → ZGC 전환 | 10,500 |
| Phase 3 | 애플리케이션 | 세션 jitter + DB 배치 버퍼 | 16,400 |
| Phase 4 | 컨테이너 런타임 | ephemeral 포트 확장 + Docker ulimit | **24,000** |

---

## 5. 운영 & 장애 복구

### 장애 복구 전략

**PEL(Pending Entry List) 재처리**  
Redis Stream Consumer Group에서 ACK되지 않은 메시지를 스케줄러가 주기적으로 회수합니다. 재시도 횟수에 따라 Exponential Backoff를 적용하고, 소진 시 `PERMANENTLY_FAILED` 처리 + 내부 이벤트를 발행합니다.

**Stuck 이벤트 복구**  
`PROCESSING` 상태에서 일정 시간 이상 경과한 이벤트를 `PENDING`으로 복구합니다. 분산 환경에서 인스턴스가 재시작될 때 처리 중이던 이벤트가 영구적으로 멈추는 것을 방지합니다. `maxStuckRetry` 초과 시 복구를 중단하여 무한 루프를 차단합니다.

**중복 처리 방지**  
`notifications(event_id, member_id)` UNIQUE 제약으로 다중 인스턴스 환경에서 동일 이벤트가 중복 저장되지 않습니다. 예약 이벤트 클레임은 Redis 분산 락으로 단일 인스턴스만 처리하도록 보장합니다.

### 모니터링

Prometheus + Grafana 대시보드를 구성하여 SSE 연결 수, GC, DB 커넥션 풀, 이벤트 처리 상태를 실시간으로 관측합니다. 이벤트 처리 실패가 조용히 사라지지 않도록 모든 실패 경로에 로그와 상태 전이를 명시했습니다.

**heap-guard.sh**: 부하 테스트 중 Docker 메모리가 92%를 초과하면 k6를 자동 종료하는 스크립트를 직접 구현했습니다. 개발 환경에서 호스트 전체가 응답 불능이 되는 상황을 방지하기 위한 안전장치입니다.

---

## 6. 성과

| 항목 | 수치 |
|---|---|
| SSE 동시 연결 | 200 → **24,000 CCU (120배)** |
| JVM STW | G1GC 2~3초 → ZGC **1ms 미만** |
| 알림 유실률 | **0%** (PEL 재처리 + UNIQUE 제약) |
| K6 서버 강제 종료 | **0건** (sse_server_disconnect = 0) |
| K6 풀 고갈 | **0건** (sse_pool_exhausted = 0) |
| DB 마이그레이션 | V1~V8 (Flyway 불변 관리) |
| 테스트 파일 | 27개 (단위 / 통합 분리) |

---

## 7. 회고

처음 SSE를 도입할 때는 "연결을 열고 메시지를 보내면 된다"고 생각했습니다. 그러나 실제 부하 테스트에서 마주한 문제는 코드 한 줄의 버그가 아니라, OS · Docker · JVM · Reactor Netty · 애플리케이션이 전부 연결된 시스템 전체의 병목이었습니다.

가장 인상 깊었던 점은 **계층별 병목이 순차적으로 드러난다**는 것입니다. OS를 해결하면 JVM이, JVM을 해결하면 애플리케이션 구조가, 그것을 해결하면 컨테이너 런타임이 새 병목으로 등장했습니다. 문제가 사라진 것이 아니라 다음 계층의 한계가 보이게 된 것이었습니다.

`kern.maxfilesperproc`이 왜 존재하는지, Docker 컨테이너의 ulimit이 왜 호스트 OS와 독립인지, ZGC와 G1GC의 차이가 실제 서비스에서 어떻게 드러나는지 — 문서에서 읽었던 것들을 수치와 장애 상황으로 직접 체험했습니다.

성능은 코드가 아니라 시스템 전체를 이해해야 개선할 수 있다는 것을 이번 작업으로 체득했습니다.

---

## 이력서용 요약

```
Notification Service — 실시간 이벤트 기반 알림 시스템
기간: 2026.06 ~ 2026.07 | 역할: 백엔드 개발 (단독)
스택: Java 25 · Spring Boot 4 · WebFlux · Redis Stream/Pub/Sub · Kafka · PostgreSQL · Docker

• Redis Stream Consumer Group 기반 이벤트 드리븐 알림 파이프라인 설계
  - Kafka → Redis Stream → FanoutService → SSE / Email 멀티채널 전달 구조 구현
  - PEL 재처리 + Exponential Backoff + 분산 락으로 알림 유실률 0% 달성

• SSE 동시 연결 200 → 24,000 CCU (120배) 달성 (MacBook Air M2 로컬 Docker 기준)
  - OS(fd/TCP 큐) → JVM(G1GC→ZGC, STW 2~3초→1ms 미만) → 애플리케이션(세션 jitter/배치버퍼)
    → 컨테이너(ephemeral 포트/Docker ulimit) 순으로 계층별 병목 직접 분석 및 해결
  - K6 24,000 VU 부하 테스트 설계 및 Prometheus/Grafana 기반 실시간 모니터링 구축

• 이벤트 타입 동적 등록 — 코드 배포 없이 런타임에 신규 이벤트 타입 추가 가능
• SSE lastEventId 기반 Replay — 네트워크 단절 후 재연결 시 미수신 알림 최대 200건 복구
• Redis Pub/Sub 브로드캐스트 — 다중 인스턴스 환경에서 인스턴스 경계 없이 SSE 전달
```
