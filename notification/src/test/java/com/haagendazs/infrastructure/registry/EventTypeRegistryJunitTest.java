package com.haagendazs.infrastructure.registry;

import com.haagendazs.domain.model.EventTypeDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;

class EventTypeRegistryJunitTest {

    private EventTypeRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new EventTypeRegistry();
    }

    @Test
    @DisplayName("등록된 정의를 code로 조회할 수 있다")
    void getByCode_returnsDefinition_whenRegistered() {
        EventTypeDefinition def = EventTypeDefinition.of(
                "TICKET_OPEN", "notif:stream:ticket.opened",
                true, false, "memberId", "saleStartAt", 0);
        registry.register(def);

        Optional<EventTypeDefinition> result = registry.getByCode("TICKET_OPEN");

        assertThat(result).isPresent();
        assertThat(result.get().getStreamKey()).isEqualTo("notif:stream:ticket.opened");
    }

    @Test
    @DisplayName("등록된 정의를 streamKey로 조회할 수 있다")
    void getByStreamKey_returnsDefinition_whenRegistered() {
        EventTypeDefinition def = EventTypeDefinition.of(
                "TICKET_OPEN", "notif:stream:ticket.opened",
                true, false, "memberId", "saleStartAt", 0);
        registry.register(def);

        Optional<EventTypeDefinition> result = registry.getByStreamKey("notif:stream:ticket.opened");

        assertThat(result).isPresent();
        assertThat(result.get().getCode()).isEqualTo("TICKET_OPEN");
    }

    @Test
    @DisplayName("미등록 code는 빈 Optional 반환")
    void getByCode_returnsEmpty_whenNotRegistered() {
        assertThat(registry.getByCode("UNKNOWN")).isEmpty();
    }

    @Test
    @DisplayName("getAllStreamKeys는 등록된 모든 streamKey 반환")
    void getAllStreamKeys_returnsAllKeys() {
        registry.register(EventTypeDefinition.of("A", "notif:stream:a", false, true, "memberId", null, 0));
        registry.register(EventTypeDefinition.of("B", "notif:stream:b", false, true, "memberId", null, 0));

        assertThat(registry.getAllStreamKeys()).containsExactlyInAnyOrder(
                "notif:stream:a", "notif:stream:b");
    }
}
