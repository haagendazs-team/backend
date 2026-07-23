# Redis XADD Timeout 및 알림 전송 안정화 플랜

## 운영 목표

| 항목 | t4g.medium (운영) | Local Mac (개발 검증) |
|---|---|---|
| 동시 SSE 연결 (CCU) | 6,000 | 24,000 |
| 알림 발송 처리량 | 1,000건/s | 1,000건/s |
| e2e 지연 p99 | < 3,000ms | < 3,000ms |
| `QueryTimeoutException` | 0 | 0 |

> k6 Phase 1/2 검증은 Local Mac(24,000 CCU)에서 먼저 수행한다.  
> t4g.medium 배포 시에는 CCU 목표를 6,000으로 조정해 동일한 테스트를 반복한다.

**리소스 배분 (2 vCPU / 4GB RAM)**

| 역할 | CPU | RAM |
|---|---|---|
| 연결 처리 (Netty SSE) | 0.9 vCPU | 2 GB |
| 전송 처리 (XADD → consume → dispatch) | 0.9 vCPU | 1 GB |
| DB / Redis | 0.2 vCPU | 1 GB |

> 연결은 튜닝으로 확장 가능하지만, **전송은 고정 리소스 내에서 안정적으로 동작**해야 한다.  
> 발송 TPS를 올리는 것보다 목표 TPS 구간에서 타임아웃·OOM 없이 돌아가는 것이 우선이다.

---

## 현재 증상과 원인

### 증상

```
QueryTimeoutException: Redis command timed out
EXISTS. Command timed out after 1 minute(s)
```

`EXISTS`는 O(1)이다. 1분 타임아웃은 명령이 느린 것이 아니라 **Lettuce 커넥션 큐에서 1분 대기**했다는 의미다.

### 원인: 단일 공유 커넥션 포화

현재 `application.yml`에 `spring.data.redis.lettuce.pool.*` 설정이 없다.  
Lettuce reactive는 기본적으로 **단일 공유 커넥션(shareNativeConnection=true)**을 사용하며, 아래 명령들이 하나의 커넥션 위에서 경쟁한다.

| 경로 | 명령 | 40k XADD/s 시 부하 |
|---|---|---|
| `ReactiveRedisStreamEventPublisher` | `XADD` | 40,000/s (한계 탐색 구간) |
| `SseEmitterManager.send()` | `PUBLISH` | fanout 대상 수 |
| `SseEmitterManager.subscribe()` | `SET` / `DEL` | SSE 연결 수 |
| `SseEmitterManager.isConnected()` | `EXISTS` | 로컬 캐시 미스 시 |
| `StreamSubscriptionManager` | `XREADGROUP` | CONSUME_CONCURRENCY=64 |

XADD 폭주가 커넥션 큐를 가득 채우면 `EXISTS` 같은 단순 명령도 60s 타임아웃까지 대기한다.

---

## 대책

### 1단계 — 빠른 실패 신호 확보 (즉시 적용)

`command-timeout`을 60s → 5s로 단축한다. 타임아웃이 빠르게 발생해야 Prometheus에서 문제 구간을 정확히 포착할 수 있다.

```yaml
# application.yml
spring:
  data:
    redis:
      lettuce:
        command-timeout: 5s
```

### 2단계 — publish 경로와 subscribe/SSE 경로 커넥션 분리 (핵심)

XADD 폭주가 `XREADGROUP`, `EXISTS`, `PUBLISH`를 막는 구조를 끊어낸다.  
`ReactiveRedisStreamEventPublisher` 전용 `ConnectionFactory`를 분리한다.

```java
// RedisStreamsConfig.java 에 추가
@Bean("publishConnectionFactory")
public ReactiveRedisConnectionFactory publishConnectionFactory(
        RedisProperties redisProperties) {
    RedisStandaloneConfiguration standalone =
            new RedisStandaloneConfiguration(redisProperties.getHost(), redisProperties.getPort());
    if (StringUtils.hasText(redisProperties.getPassword())) {
        standalone.setPassword(redisProperties.getPassword());
    }

    LettuceClientConfiguration clientConfig = LettuceClientConfiguration.builder()
            .commandTimeout(Duration.ofSeconds(5))
            .build();

    return new LettuceConnectionFactory(standalone, clientConfig);
}

@Bean("publishRedisTemplate")
public ReactiveStringRedisTemplate publishRedisTemplate(
        @Qualifier("publishConnectionFactory") ReactiveRedisConnectionFactory factory) {
    return new ReactiveStringRedisTemplate(factory);
}
```

```java
// ReactiveRedisStreamEventPublisher.java
@Component
@RequiredArgsConstructor
public class ReactiveRedisStreamEventPublisher {

    @Qualifier("publishRedisTemplate")
    private final ReactiveStringRedisTemplate redisTemplate;
    // ...
}
```

### 3단계 — 전송 처리량 고정 (back-pressure)

목표 TPS(1,000건/s = 20 iter/s × 50)를 초과하는 XADD 요청을 서버에서 선제 차단한다.  
Redis가 포화되기 전에 `503`을 반환해 큐가 쌓이지 않도록 한다.

```java
// ReactiveRedisStreamEventPublisher.java
// 동시 진행 중인 publish Mono 수를 AtomicInteger로 추적
private static final int MAX_CONCURRENT_PUBLISH = 40; // 1,000 알림/s ÷ 25ms avg = 40
private final AtomicInteger inFlight = new AtomicInteger(0);

public Mono<Void> publish(List<?> envelopes) {
    if (inFlight.get() >= MAX_CONCURRENT_PUBLISH) {
        publishErrorTotal.increment();
        return Mono.error(new PublishOverloadException("publish 과부하 — 요청 거부"));
    }
    inFlight.incrementAndGet();
    return doPublish(envelopes)
            .doFinally(s -> inFlight.decrementAndGet());
}
```

`NotificationModuleController`에서 `PublishOverloadException`을 `503`으로 매핑한다.

### 4단계 — k6 테스트 재설계 (목표 TPS 검증 중심)

현재 k6는 한계 탐색(40k XADD/s까지 무제한 램프업) 중심이다.  
목표 TPS 구간에서의 안정성을 먼저 검증하는 2단계 구조로 변경한다.

**Phase 1 — 연결 안정성**

```js
// Local Mac: vus=24000 / t4g.medium: vus=6000
scenarios: {
    sseHolder: {
        executor: 'constant-vus',
        vus: 24000,
        duration: '5m',
        exec: 'holdSse',
    },
    // bulkSender 없음 — 연결만 유지
}
```

`sse_active_connections` (Prometheus Gauge)가 목표 CCU에 수렴하고 유지되면 통과.

**Phase 2 — 전송 성능 (1,000 알림/s)**

```js
// Local Mac: vus=24000 / t4g.medium: vus=6000
scenarios: {
    sseHolder: { vus: 24000, duration: '10m', exec: 'holdSse' },
    bulkSender: {
        executor: 'constant-arrival-rate',
        rate: 20,         // 20 iter/s × BULK_SIZE=50 = 1,000 알림/s
        timeUnit: '1s',
        preAllocatedVUs: 50,
        duration: '8m',
        startTime: '2m',  // SSE 연결 안정 후 시작
        exec: 'sendBulk',
    },
}
```

`ramping-arrival-rate`(무제한 상승)는 한계 탐색 전용으로만 사용한다. 안정성 검증에는 `constant-arrival-rate`를 쓴다.

---

## 적용 순서

| 순서 | 작업 | 확인 지표 |
|---|---|---|
| 1 | `command-timeout: 5s` 적용 | `stream_publish_error_total` 발생 시 즉시 포착 |
| 2 | publish 전용 커넥션 분리 | EXISTS 타임아웃 소멸 여부 |
| 3 | k6 Phase 1 실행 (Local: CCU 24k / 운영: CCU 6k) | `sse_active_connections` 수렴, CPU ≤ 0.9 vCPU |
| 4 | k6 Phase 2 실행 (Phase 1 CCU + 1,000 알림/s) | e2e p99 < 3s, 타임아웃 0 |
| 5 | 3단계 back-pressure 적용 | `stream_publish_error_total` 0, `publish_overload` 카운터 확인 |
| 6 | 한계 탐색 재실행 (ramping) | 서버가 과부하 시 503 반환하고 안정 유지 |

---

## 성공 기준

| 지표 | Local Mac | t4g.medium (운영) |
|---|---|---|
| `sse_active_connections` Phase 1 수렴 | 24,000 | 6,000 |
| `QueryTimeoutException` | 0 | 0 |
| e2e_latency_ms p99 | < 3,000ms | < 3,000ms |
| bulk_publish_success_rate | ≥ 0.95 | ≥ 0.95 |
| publish vs consume gap | < 10% | < 10% |
| JVM 힙 사용률 | < 80% | < 80% |

---

## 현재 코드 위치

| 파일 | 현재 상태 |
|---|---|
| `infrastructure/publisher/ReactiveRedisStreamEventPublisher.java` | XADD, flatMap concurrency=16 |
| `infrastructure/config/RedisStreamsConfig.java` | 커넥션 분리 추가 대상 |
| `infrastructure/sse/SseEmitterManager.java` | EXISTS 로컬 캐시 적용됨 |
| `resources/application.yml` | lettuce.pool 미설정, command-timeout 기본(60s) |
| `k6/notification/send.js` | ramping 무제한 상승 구조 |
