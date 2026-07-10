package com.haagendazs.application.service;

import com.haagendazs.domain.model.EventTypeDefinition;
import com.haagendazs.domain.repository.EventTypeRepository;
import com.haagendazs.infrastructure.registry.EventTypeRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventTypeRegistrationServiceJunitTest {

    @InjectMocks
    private EventTypeRegistrationService service;

    @Mock
    private EventTypeRepository eventTypeRepository;

    @Mock
    private EventTypeRegistry eventTypeRegistry;

    @Mock
    private ReactiveStringRedisTemplate redisTemplate;

    @Test
    @DisplayName("유효한 요청으로 이벤트 타입을 등록하면 저장된 정의를 반환한다")
    void register_savesAndReturnsDefinition() {
        // GIVEN
        EventTypeDefinition saved = EventTypeDefinition.of("NEW_EVENT", false, true);

        when(eventTypeRepository.existsByCode("NEW_EVENT")).thenReturn(Mono.just(false));
        when(eventTypeRepository.save(any())).thenReturn(Mono.just(saved));
        doNothing().when(eventTypeRegistry).register(any());
        when(redisTemplate.convertAndSend(anyString(), anyString())).thenReturn(Mono.just(1L));

        // WHEN
        EventTypeDefinition result = service.register("NEW_EVENT", false, true).block();

        // THEN
        assertThat(result).isNotNull();
        assertThat(result.getCode()).isEqualTo("NEW_EVENT");
        assertThat(result.isSingleTarget()).isTrue();
    }

    @Test
    @DisplayName("Redis pub/sub 채널로 이벤트 코드를 발행한다")
    void register_publishesEventCodeToRedis() {
        // GIVEN
        EventTypeDefinition saved = EventTypeDefinition.of("TICKET_OPEN", true, false);

        when(eventTypeRepository.existsByCode("TICKET_OPEN")).thenReturn(Mono.just(false));
        when(eventTypeRepository.save(any())).thenReturn(Mono.just(saved));
        doNothing().when(eventTypeRegistry).register(any());
        when(redisTemplate.convertAndSend(anyString(), anyString())).thenReturn(Mono.just(1L));

        // WHEN
        EventTypeDefinition result = service.register("TICKET_OPEN", true, false).block();

        // THEN
        assertThat(result).isNotNull();
        assertThat(result.isScheduled()).isTrue();
    }
}
