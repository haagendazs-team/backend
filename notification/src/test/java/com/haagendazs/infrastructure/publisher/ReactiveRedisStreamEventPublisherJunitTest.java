package com.haagendazs.infrastructure.publisher;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.haagendazs.domain.model.NotificationEnvelope;
import com.haagendazs.infrastructure.config.RedisStreamsConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.ReactiveStreamOperations;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReactiveRedisStreamEventPublisherJunitTest {

    @Mock
    private ReactiveStringRedisTemplate redisTemplate;

    private ReactiveRedisStreamEventPublisher publisher;

    @SuppressWarnings("unchecked")
    private ReactiveStreamOperations<String, Object, Object> stubStreamOps() {
        ReactiveStreamOperations<String, Object, Object> streamOps = mock(ReactiveStreamOperations.class);
        when(redisTemplate.opsForStream()).thenReturn((ReactiveStreamOperations) streamOps);
        when(streamOps.add(eq(RedisStreamsConfig.STREAM_KEY), any(Map.class)))
                .thenReturn(Mono.just(RecordId.of("0-1")));
        return streamOps;
    }

    @BeforeEach
    void setUp() {
        publisher = new ReactiveRedisStreamEventPublisher(
                redisTemplate, new ObjectMapper(), new SimpleMeterRegistry());
        publisher.initMetrics();
    }

    @Test
    @DisplayName("단건 publish는 XADD를 1회 호출한다")
    void publish_singleEnvelope_callsXAddOnce() {
        ReactiveStreamOperations<String, Object, Object> streamOps = stubStreamOps();
        NotificationEnvelope envelope = NotificationEnvelope.of(
                1L, NotificationEnvelope.DispatchType.IMMEDIATE, null, Map.of("key", "val"));

        publisher.publish(envelope).block();

        verify(streamOps, times(1)).add(eq(RedisStreamsConfig.STREAM_KEY), any(Map.class));
    }

    @Test
    @DisplayName("빈 리스트 publish는 XADD를 호출하지 않는다")
    void publish_emptyList_noXAdd() {
        assertThatNoException().isThrownBy(() -> publisher.publish(List.of()).block());
    }

    @Test
    @DisplayName("복수 envelope publish는 count만큼 XADD를 호출한다")
    void publish_multipleEnvelopes_callsXAddForEach() {
        ReactiveStreamOperations<String, Object, Object> streamOps = stubStreamOps();
        List<NotificationEnvelope> envelopes = List.of(
                NotificationEnvelope.of(1L, NotificationEnvelope.DispatchType.IMMEDIATE, null, null),
                NotificationEnvelope.of(2L, NotificationEnvelope.DispatchType.IMMEDIATE, null, null),
                NotificationEnvelope.of(3L, NotificationEnvelope.DispatchType.IMMEDIATE, null, null)
        );

        publisher.publish(envelopes).block();

        verify(streamOps, times(3)).add(eq(RedisStreamsConfig.STREAM_KEY), any(Map.class));
    }

    @Test
    @DisplayName("publishWithEventTypeCode는 eventTypeCode를 payload에 주입해 XADD한다")
    void publishWithEventTypeCode_injectsEventTypeCode() {
        ReactiveStreamOperations<String, Object, Object> streamOps = stubStreamOps();
        NotificationEnvelope envelope = NotificationEnvelope.of(
                1L, NotificationEnvelope.DispatchType.IMMEDIATE, null, Map.of("publishedAt", 12345L));

        publisher.publishWithEventTypeCode(List.of(envelope), "PAYMENT_COMPLETED").block();

        verify(streamOps, times(1)).add(eq(RedisStreamsConfig.STREAM_KEY), any(Map.class));
    }

    @Test
    @DisplayName("publishWithEventTypeCode 빈 리스트는 XADD를 호출하지 않는다")
    void publishWithEventTypeCode_emptyList_noXAdd() {
        assertThatNoException().isThrownBy(
                () -> publisher.publishWithEventTypeCode(List.of(), "PAYMENT_COMPLETED").block());
    }

    @Test
    @DisplayName("republish는 rawPayload 그대로 XADD한다")
    void republish_sendsRawPayload() {
        ReactiveStreamOperations<String, Object, Object> streamOps = stubStreamOps();

        publisher.republish("{\"eventTypeCode\":\"TEST\"}").block();

        verify(streamOps, times(1)).add(eq(RedisStreamsConfig.STREAM_KEY), any(Map.class));
    }
}
