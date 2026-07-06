package com.haagendazs.application.service;

import com.haagendazs.domain.model.PingStatus;
import com.haagendazs.presentation.dto.PingResultResponse;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

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
}
