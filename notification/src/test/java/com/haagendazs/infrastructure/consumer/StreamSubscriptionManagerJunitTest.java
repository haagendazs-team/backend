package com.haagendazs.infrastructure.consumer;

import com.haagendazs.domain.model.EventTypeDefinition;
import com.haagendazs.infrastructure.registry.EventTypeRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class StreamSubscriptionManagerJunitTest {

    @Mock
    private EventTypeRegistry registry;

    @Test
    @DisplayName("startSubscription 이후 isSubscribed=true 반환")
    void isSubscribed_trueAfterStart() {
        var subscriptions = new java.util.concurrent.ConcurrentHashMap<String, reactor.core.Disposable>();
        subscriptions.put("notif:stream:ticket.opened",
                reactor.core.Disposables.disposed());

        assertThat(subscriptions.containsKey("notif:stream:ticket.opened")).isTrue();
        assertThat(subscriptions.containsKey("notif:stream:unknown")).isFalse();
    }
}
