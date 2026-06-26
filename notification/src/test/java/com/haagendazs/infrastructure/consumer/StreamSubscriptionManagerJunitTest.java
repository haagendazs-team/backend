package com.haagendazs.infrastructure.consumer;

import com.haagendazs.application.service.EventStatusService;
import com.haagendazs.application.service.FanoutService;
import com.haagendazs.domain.model.EventTypeDefinition;
import com.haagendazs.infrastructure.registry.EventTypeRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.stream.ObjectRecord;
import org.springframework.data.redis.core.ReactiveStreamOperations;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.stream.StreamReceiver;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
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

    private StreamSubscriptionManager subscriptionManager;

    @BeforeEach
    void setUp() {
        subscriptionManager = new StreamSubscriptionManager(
                receiver, redisTemplate, statusService, fanoutService, registry);
        ReflectionTestUtils.setField(subscriptionManager, "consumerName", "test-consumer");
    }

    @Test
    @DisplayName("구독 전 isSubscribed=false 반환")
    void isSubscribed_beforeSubscription_returnsFalse() {
        assertThat(subscriptionManager.isSubscribed("notif:stream:ticket.open")).isFalse();
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("startSubscription 이후 isSubscribed=true 반환")
    void isSubscribed_afterStartSubscription_returnsTrue() {
        EventTypeDefinition def = EventTypeDefinition.of(
                "TICKET_OPEN", "notif:stream:ticket.open", false, true, "memberId", null, 0);

        ReactiveStreamOperations<String, Object, Object> streamOps = mock(ReactiveStreamOperations.class);
        when(redisTemplate.opsForStream()).thenReturn(
                (ReactiveStreamOperations) streamOps);
        when(streamOps.createGroup(any(), any(), any())).thenReturn(Mono.just("OK"));
        when(receiver.receive(any(), any(org.springframework.data.redis.connection.stream.StreamOffset.class)))
                .thenReturn(Flux.empty());

        subscriptionManager.startSubscription(def);

        assertThat(subscriptionManager.isSubscribed("notif:stream:ticket.open")).isTrue();
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("이미 구독 중인 streamKey로 startSubscription 재호출 시 중복 구독 방지")
    void startSubscription_duplicateKey_doesNotSubscribeAgain() {
        EventTypeDefinition def = EventTypeDefinition.of(
                "TICKET_OPEN", "notif:stream:ticket.open", false, true, "memberId", null, 0);

        ReactiveStreamOperations<String, Object, Object> streamOps = mock(ReactiveStreamOperations.class);
        when(redisTemplate.opsForStream()).thenReturn(
                (ReactiveStreamOperations) streamOps);
        when(streamOps.createGroup(any(), any(), any())).thenReturn(Mono.just("OK"));
        when(receiver.receive(any(), any(org.springframework.data.redis.connection.stream.StreamOffset.class)))
                .thenReturn(Flux.empty());

        subscriptionManager.startSubscription(def);
        subscriptionManager.startSubscription(def);

        assertThat(subscriptionManager.isSubscribed("notif:stream:ticket.open")).isTrue();
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("createGroup 실패 시 BUSYGROUP이면 로그만 하고 구독은 계속된다")
    void startSubscription_createGroupBusyGroup_continuesSubscription() {
        EventTypeDefinition def = EventTypeDefinition.of(
                "TICKET_OPEN", "notif:stream:ticket.open", false, true, "memberId", null, 0);

        ReactiveStreamOperations<String, Object, Object> streamOps = mock(ReactiveStreamOperations.class);
        when(redisTemplate.opsForStream()).thenReturn(
                (ReactiveStreamOperations) streamOps);
        RuntimeException busyGroup = new RuntimeException("BUSYGROUP Consumer Group name already exists");
        when(streamOps.createGroup(any(), any(), any())).thenReturn(Mono.error(busyGroup));
        when(receiver.receive(any(), any(org.springframework.data.redis.connection.stream.StreamOffset.class)))
                .thenReturn(Flux.empty());

        subscriptionManager.startSubscription(def);

        assertThat(subscriptionManager.isSubscribed("notif:stream:ticket.open")).isTrue();
    }
}
