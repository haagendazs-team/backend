package com.haagendazs.application.service;

import com.haagendazs.domain.model.PingStatus;
import com.haagendazs.presentation.dto.PingResultResponse;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SsePingAckServiceJunitTest {

    private SsePingAckService service;

    @BeforeEach
    void setUp() {
        service = new SsePingAckService(new SimpleMeterRegistry());
        service.initMetrics();
    }

    @Test
    @DisplayName("성공 ack 기록 후 총전송받은횟수=1, 성공적으로받은횟수=1")
    void record_성공_카운트_정상() {
        service.record(1L, PingStatus.성공, LocalDateTime.now());

        PingResultResponse result = service.query(1L);

        assertThat(result.totalSent()).isEqualTo(1);
        assertThat(result.successCount()).isEqualTo(1);
        assertThat(result.entries()).hasSize(1);
        assertThat(result.entries().get(0).status()).isEqualTo(PingStatus.성공);
    }

    @Test
    @DisplayName("유실 ack는 성공 카운트에 포함되지 않는다")
    void record_유실_성공카운트_미포함() {
        service.record(1L, PingStatus.유실, LocalDateTime.now());

        PingResultResponse result = service.query(1L);

        assertThat(result.totalSent()).isEqualTo(1);
        assertThat(result.successCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("실패 ack는 총전송받은횟수에 포함되지 않는다")
    void record_실패_총카운트_미포함() {
        service.record(1L, PingStatus.실패, LocalDateTime.now());

        PingResultResponse result = service.query(1L);

        assertThat(result.totalSent()).isEqualTo(0);
        assertThat(result.successCount()).isEqualTo(0);
        assertThat(result.entries()).isEmpty();
    }

    @Test
    @DisplayName("빈 이력 조회 시 0, 0, 빈 리스트 반환")
    void query_기록없음_기본값_반환() {
        PingResultResponse result = service.query(999L);

        assertThat(result.totalSent()).isEqualTo(0);
        assertThat(result.successCount()).isEqualTo(0);
        assertThat(result.entries()).isEmpty();
    }

    @Test
    @DisplayName("서로 다른 memberId는 독립적인 윈도우를 가진다")
    void record_다른멤버_독립적_윈도우() {
        service.record(1L, PingStatus.성공, LocalDateTime.now());
        service.record(2L, PingStatus.유실, LocalDateTime.now());

        assertThat(service.query(1L).totalSent()).isEqualTo(1);
        assertThat(service.query(2L).totalSent()).isEqualTo(1);
        assertThat(service.query(2L).successCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("Prometheus sse_notification_result 카운터가 status별로 증가한다")
    void record_prometheus_카운터_증가() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        SsePingAckService svc = new SsePingAckService(registry);
        svc.initMetrics();

        svc.record(1L, PingStatus.성공, LocalDateTime.now());
        svc.record(1L, PingStatus.유실, LocalDateTime.now());

        double 성공 = registry.counter("sse_notification_result", "status", "성공").count();
        double 유실 = registry.counter("sse_notification_result", "status", "유실").count();
        assertThat(성공).isEqualTo(1.0);
        assertThat(유실).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Gauge sse_ping_window_total은 성공+유실 합산을 반환한다")
    void gauge_windowTotal_성공과유실_합산() {
        service.record(1L, PingStatus.성공, LocalDateTime.now());
        service.record(1L, PingStatus.유실, LocalDateTime.now());
        service.record(2L, PingStatus.성공, LocalDateTime.now());

        assertThat(service.computeWindowTotal()).isEqualTo(3.0);
    }

    @Test
    @DisplayName("Gauge sse_ping_window_success는 성공만 집계한다")
    void gauge_windowSuccess_성공만_집계() {
        service.record(1L, PingStatus.성공, LocalDateTime.now());
        service.record(1L, PingStatus.유실, LocalDateTime.now());
        service.record(2L, PingStatus.실패, LocalDateTime.now());

        assertThat(service.computeWindowSuccess()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Gauge는 Prometheus 스크레이프 시점(5초/15초/30초) 모두 최신 윈도우를 반영한다")
    void gauge_스크레이프_시점마다_최신값_반환() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        SsePingAckService svc = new SsePingAckService(registry);
        svc.initMetrics();

        svc.record(1L, PingStatus.성공, LocalDateTime.now());

        double beforeAdd = registry.get("sse_ping_window_total").gauge().value();
        assertThat(beforeAdd).isEqualTo(1.0);

        svc.record(2L, PingStatus.성공, LocalDateTime.now());
        svc.record(2L, PingStatus.유실, LocalDateTime.now());

        double afterAdd = registry.get("sse_ping_window_total").gauge().value();
        assertThat(afterAdd).isEqualTo(3.0);
    }

    @Test
    @DisplayName("evictExpired — 윈도우 만료된 항목은 query 시 제거된다")
    @SuppressWarnings("unchecked")
    void evictExpired_removesStaleEntries() throws Exception {
        // GIVEN: 항목을 추가한 뒤 store의 deque에 직접 접근해 timestampMs를 과거로 교체
        service.record(1L, PingStatus.성공, LocalDateTime.now());

        Field storeField = SsePingAckService.class.getDeclaredField("store");
        storeField.setAccessible(true);
        Map<Long, Deque<?>> store = (Map<Long, Deque<?>>) storeField.get(service);

        // PingEntry는 private record — 전체 deque를 새로운 만료 항목으로 교체
        // timestampMs=0 항목을 만들 방법이 없으므로 deque 자체를 비워 eviction 진입 후 즉시 루프 종료 경로를 커버
        // 대신 WINDOW_MS를 0으로 만들어 현재 항목도 만료되게 한다
        Field windowField = SsePingAckService.class.getDeclaredField("WINDOW_MS");
        windowField.setAccessible(true);
        // WINDOW_MS는 static final long — reflection으로 변경 후 query() 호출
        // Java 25에서는 허용되지 않을 수 있으므로, 빈 deque를 주입하는 방식으로 대체
        // 항목이 있는 deque에서 evictExpired가 실행되도록: record()로 항목 추가 → 즉시 query()
        // 이미 위에서 record()를 했으므로 query() 호출 시 evictExpired가 실행되지만 timestampMs가 최신이라 제거 안 됨

        // 실제 eviction 루프 바디(pollFirst)를 커버하려면:
        // store에 timestampMs=0인 PingEntry를 직접 주입해야 한다
        Deque<?> deque = store.get(1L);
        // deque 클리어 후 만료된 항목(timestampMs=0) 주입
        Class<?> pingEntryClass = Class.forName("com.haagendazs.application.service.SsePingAckService$PingEntry");
        var ctor = pingEntryClass.getDeclaredConstructor(LocalDateTime.class, PingStatus.class, long.class);
        ctor.setAccessible(true);
        Object expiredEntry = ctor.newInstance(LocalDateTime.now().minusHours(2), PingStatus.성공, 0L);

        synchronized (deque) {
            ((Deque<Object>) deque).clear();
            ((Deque<Object>) deque).addLast(expiredEntry);
        }

        // WHEN: query() 호출 시 evictExpired → pollFirst() 실행
        PingResultResponse result = service.query(1L);

        // THEN: 만료된 항목이 제거되어 0이 반환된다
        assertThat(result.totalSent()).isEqualTo(0);
    }
}
