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
        EventTypeDefinition def = EventTypeDefinition.of("TICKET_OPEN", true, false);
        registry.register(def);

        Optional<EventTypeDefinition> result = registry.getByCode("TICKET_OPEN");

        assertThat(result).isPresent();
        assertThat(result.get().getCode()).isEqualTo("TICKET_OPEN");
    }

    @Test
    @DisplayName("미등록 code는 빈 Optional 반환")
    void getByCode_returnsEmpty_whenNotRegistered() {
        assertThat(registry.getByCode("UNKNOWN")).isEmpty();
    }

    @Test
    @DisplayName("getAllDefinitions는 등록된 모든 정의 반환")
    void getAllDefinitions_returnsAllRegistered() {
        registry.register(EventTypeDefinition.of("A", false, true));
        registry.register(EventTypeDefinition.of("B", false, true));

        assertThat(registry.getAllDefinitions()).hasSize(2);
    }
}
