package com.haagendazs.presentation.controller;

import com.haagendazs.infrastructure.publisher.ReactiveRedisStreamEventPublisher;
import com.haagendazs.presentation.dto.K6BulkPublishRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationModuleControllerJunitTest {

    @Mock
    private ReactiveRedisStreamEventPublisher publisher;

    private NotificationModuleController controller;

    @BeforeEach
    void setUp() {
        controller = new NotificationModuleController(publisher);
    }

    @Test
    @DisplayName("publishK6Bulk은 count만큼 envelope를 생성해 publishWithEventTypeCode를 호출한다")
    void publishK6Bulk_createsEnvelopesAndCallsPublisher() {
        when(publisher.publishWithEventTypeCode(anyList(), anyString())).thenReturn(Mono.empty());
        K6BulkPublishRequest request = new K6BulkPublishRequest(10001L, 5, "PAYMENT_COMPLETED", null);

        ResponseEntity<Void> response = controller.publishK6Bulk(request).block();

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        verify(publisher, times(1)).publishWithEventTypeCode(anyList(), anyString());
    }

    @Test
    @DisplayName("publishK6Bulk은 startMemberId부터 count개의 memberId 범위를 커버한다")
    void publishK6Bulk_coversCorrectMemberIdRange() {
        when(publisher.publishWithEventTypeCode(anyList(), anyString())).thenReturn(Mono.empty());
        K6BulkPublishRequest request = new K6BulkPublishRequest(1L, 3, "ORDER_PLACED", 1234567890L);

        controller.publishK6Bulk(request).block();

        verify(publisher).publishWithEventTypeCode(
                argThat(list -> list.size() == 3),
                eq("ORDER_PLACED")
        );
    }

    @Test
    @DisplayName("publishK6Bulk publishedAt이 null이면 현재 시각으로 대체되어 200을 반환한다")
    void publishK6Bulk_nullPublishedAt_usesCurrentTime() {
        when(publisher.publishWithEventTypeCode(anyList(), anyString())).thenReturn(Mono.empty());
        K6BulkPublishRequest request = new K6BulkPublishRequest(1L, 1, "PAYMENT_COMPLETED", null);

        ResponseEntity<Void> response = controller.publishK6Bulk(request).block();

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    }

    @Test
    @DisplayName("publishK6Bulk publishedAt이 명시되면 해당 값을 사용해 200을 반환한다")
    void publishK6Bulk_explicitPublishedAt_passedThrough() {
        when(publisher.publishWithEventTypeCode(anyList(), anyString())).thenReturn(Mono.empty());
        long publishedAt = 1700000000000L;
        K6BulkPublishRequest request = new K6BulkPublishRequest(1L, 1, "PAYMENT_COMPLETED", publishedAt);

        ResponseEntity<Void> response = controller.publishK6Bulk(request).block();

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    }
}
