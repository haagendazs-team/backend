package com.haagendazs.infrastructure.consumer;

import com.haagendazs.application.service.EventStatusService;
import com.haagendazs.application.service.FanoutService;
import com.haagendazs.application.service.PayloadParser;
import com.haagendazs.infrastructure.registry.EventTypeRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.stream.ObjectRecord;
import org.springframework.data.redis.core.ReactiveRedisCallback;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.stream.StreamReceiver;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Flux;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StreamSubscriptionManagerJunitTest {

    @Mock
    private StreamReceiver<String, ObjectRecord<String, String>> receiver;

    @Mock
    private ReactiveStringRedisTemplate redisTemplate;

    @Mock
    private EventStatusService statusService;

    @Mock
    private FanoutService fanoutService;

    @Mock
    private EventTypeRegistry registry;

    @Mock
    private PayloadParser payloadParser;

    private StreamSubscriptionManager subscriptionManager;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        subscriptionManager = new StreamSubscriptionManager(
                receiver, redisTemplate, statusService, fanoutService, registry, payloadParser,
                new SimpleMeterRegistry());
        ReflectionTestUtils.setField(subscriptionManager, "consumerName", "test-consumer");

        // createGroupIfAbsent: low-level execute() 경로 — BUSYGROUP으로 swallow됨
        when(redisTemplate.execute(any(ReactiveRedisCallback.class))).thenReturn(Flux.just("OK"));
        when(receiver.receive(any(), any(org.springframework.data.redis.connection.stream.StreamOffset.class)))
                .thenReturn(Flux.never());
    }

    @Test
    @DisplayName("start() 이후 isSubscribed()=true 반환")
    void isSubscribed_afterStart_returnsTrue() {
        subscriptionManager.start();

        assertThat(subscriptionManager.isSubscribed()).isTrue();
    }

    @Test
    @DisplayName("stop() 이후 isSubscribed()=false 반환")
    void isSubscribed_afterStop_returnsFalse() {
        subscriptionManager.start();
        subscriptionManager.stop();

        assertThat(subscriptionManager.isSubscribed()).isFalse();
    }
}
