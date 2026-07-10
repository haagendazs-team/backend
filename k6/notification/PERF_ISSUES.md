# 알림 시스템 성능 이슈 — 현황 진단 및 개선 방향

> k6 부하 테스트 전 어드바이저 분석 결과 기록.
> k6 파일 수정 완료. 아래 항목은 서버 코드 개선 시 참조.

---

## 1. [Critical] SSE 수평 확장 불가 — 단일 인스턴스 아키텍처 제약

**현상**

```
SseEmitterManager.emitters = ConcurrentHashMap<Long, SseEmitter>
                              ↑ 인스턴스 로컬 메모리
```

`StreamConsumer`는 Redis Consumer Group으로 메시지를 수신하는데,  
Consumer Group은 메시지를 **정확히 한 인스턴스**에만 전달한다.  
인스턴스 A에서 소비한 메시지를 인스턴스 B에 연결된 유저에게 전달할 방법이 없다.

**결과**: 다중 인스턴스 배포 시 SSE 전파가 랜덤하게 누락됨.  
현재 `RedisPubSubConfig`는 스케줄 트리거 전용 — SSE 브릿지 없음.

**해결 방향**: Redis Pub/Sub 브릿지 추가

```
StreamConsumer → FanoutService → Dispatcher
    → RedisTemplate.convertAndSend("sse:deliver:{memberId}", payload)
    ← 모든 인스턴스의 MessageListener 수신
    → 본인 emitters에 해당 memberId 있으면 SseEmitter.send()
```

---

## 2. [High] HikariCP 10 커넥션 — 브로드캐스트 팬아웃 즉시 포화

**현상**

`Dispatcher.toMember()` — 사용자 1명당 최소 4 쿼리:
```
existsByEventIdAndMemberId()             // SELECT
findByMemberId(settingRepository)        // SELECT
notificationRepository.save()           // INSERT
channelRepository.findByMemberIdAndEnabledTrue()  // SELECT
historyRepository.save() × N채널        // INSERT × N
```

`ticket.opened` 브로드캐스트 3,000명 → **~15,000 쿼리**를 Hikari 10 커넥션으로 처리.  
`ChunkService`가 `REQUIRES_NEW`로 청크마다 신규 트랜잭션 — 커넥션 대기 폭발.

**임시 조치** (서버 재시작 없이):
```yaml
spring.datasource.hikari.maximum-pool-size: 50
spring.datasource.hikari.minimum-idle: 10
```

**근본 해결**: 브로드캐스트 경로에서 DB 배치 처리 도입.

---

## 3. [Medium] sendHeartbeat 단일 스레드 직렬 순회

**현상**

```java
@Scheduled(fixedDelay = 60_000)
public void sendHeartbeat() {
    emitters.forEach((memberId, emitter) -> {
        emitter.send(...);  // 3,000개 직렬 IO
    });
}
```

**해결 방향**: `parallelStream()` 또는 `CompletableFuture` 배치 처리.

---

## 4. [Low] k6 로컬 실행 — OS fd 한계

**macOS 기본값 vs 필요량**

| 제한 | 기본값 | 3K 필요 | 10K 필요 |
|------|--------|---------|---------|
| `ulimit -n` | 256 | 4,000+ | 13,000+ |
| `kern.maxfilesperproc` | 10,240 | 4,000 | 13,000 |

**3K 테스트 전 필수**:
```bash
ulimit -n 65536
sudo sysctl -w kern.maxfilesperproc=65536
sudo sysctl -w kern.maxfiles=65536
```

**10K 목표**: Linux 부하 생성기 또는 k6 Cloud 필요. 로컬 macOS에서 불가.

---

## 5. [Info] k6 receive.js SSE 파싱 한계

**현상**: `http.get`으로 SSE 스트림을 수신하면 timeout 후 body 일괄 반환.  
개별 이벤트 도달 시각 측정 불가. `sse_event_latency`는 **전체 연결 소요 시간**.

이전 테스트 로그에서 50 VU 기준 `sse_events_dropped=150(30%)` — 파싱 실패.

**해결 방향**: xk6-sse 빌드로 실시간 스트리밍 파싱:
```bash
xk6 build --with github.com/szkiba/xk6-sse
```

---

## 개선 우선순위 요약

| 순위 | 항목 | 영향 | 난이도 |
|------|------|------|--------|
| 1 | Hikari pool size 증가 | 브로드캐스트 포화 해소 | 설정 변경 |
| 2 | OS fd 한계 조정 | k6 3K 테스트 전제 조건 | OS 커맨드 |
| 3 | xk6-sse 빌드 | 수신 측정 신뢰성 | 빌드 1회 |
| 4 | sendHeartbeat 병렬화 | 3K 이상 heartbeat 안정성 | 코드 1줄 |
| 5 | Redis Pub/Sub SSE 브릿지 | 수평 확장 전제 조건 | 구조 변경 |

브로커 1 
내부 구조 변경으로 payload가 바뀔수도

브로커 2
중복 방지 캐머니즘 개선 방안
SSE 연결된 대상으로 
