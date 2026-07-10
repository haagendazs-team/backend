package com.haagendazs.infrastructure.startup;

import com.haagendazs.domain.model.EventTypeDefinition;
import com.haagendazs.domain.repository.EventTypeRepository;
import com.haagendazs.infrastructure.registry.EventTypeRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventTypeStartupLoaderJunitTest {

    @InjectMocks
    private EventTypeStartupLoader startupLoader;

    @Mock
    private EventTypeRepository eventTypeRepository;

    @Mock
    private EventTypeRegistry registry;

    @Test
    @DisplayName("run 시 활성 이벤트 타입을 레지스트리에 등록한다")
    void run_registersAllEnabledEventTypes() throws Exception {
        EventTypeDefinition def = EventTypeDefinition.of("TICKET_OPEN", false, true);
        when(eventTypeRepository.findAllEnabled()).thenReturn(Flux.just(def));
        when(registry.getAllDefinitions()).thenReturn(List.of(def));

        startupLoader.run(null);

        verify(registry).register(def);
    }

    @Test
    @DisplayName("run 시 이벤트 타입이 없으면 레지스트리에 등록하지 않는다")
    void run_noEventTypes_registersNothing() throws Exception {
        when(eventTypeRepository.findAllEnabled()).thenReturn(Flux.empty());
        when(registry.getAllDefinitions()).thenReturn(List.of());

        startupLoader.run(null);

        verify(registry, org.mockito.Mockito.never()).register(org.mockito.Mockito.any());
    }
}
