package com.haagendazs.application.service;

import com.haagendazs.common.exception.BusinessException;
import com.haagendazs.domain.exception.NotificationErrorCode;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventTypeRegistrationServiceJunitExceptionTest {

    @InjectMocks
    private EventTypeRegistrationService service;

    @Mock
    private EventTypeRepository eventTypeRepository;

    @Mock
    private EventTypeRegistry eventTypeRegistry;

    @Mock
    private ReactiveStringRedisTemplate redisTemplate;

    @Test
    @DisplayName("중복 code 등록 시 EVENT_TYPE_DUPLICATE 예외 발생")
    void register_throwsDuplicate_whenCodeAlreadyExists() {
        // GIVEN
        when(eventTypeRepository.existsByCode("TICKET_OPEN")).thenReturn(Mono.just(true));

        // WHEN & THEN
        Mono<EventTypeDefinition> result = service.register("TICKET_OPEN", true, false);

        assertThatThrownBy(result::block)
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(NotificationErrorCode.EVENT_TYPE_DUPLICATE));
    }
}
