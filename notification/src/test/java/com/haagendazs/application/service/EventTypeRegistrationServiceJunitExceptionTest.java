package com.haagendazs.application.service;

import com.haagendazs.common.exception.BusinessException;
import com.haagendazs.common.exception.ErrorCode;
import com.haagendazs.domain.model.EventTypeDefinition;
import com.haagendazs.domain.repository.EventTypeRepository;
import com.haagendazs.infrastructure.consumer.StreamSubscriptionManager;
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
    private StreamSubscriptionManager streamSubscriptionManager;

    @Mock
    private ReactiveStringRedisTemplate redisTemplate;

    @Test
    @DisplayName("중복 code 등록 시 EVENT_TYPE_DUPLICATE 예외 발생")
    void register_throwsDuplicate_whenCodeAlreadyExists() {
        // GIVEN
        when(eventTypeRepository.existsByCode("TICKET_OPEN")).thenReturn(Mono.just(true));

        // WHEN & THEN
        Mono<EventTypeDefinition> result = service.register(
                "TICKET_OPEN", "notif:stream:ticket.opened",
                true, false, "memberId", "saleStartAt", 0
        );

        assertThatThrownBy(result::block)
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.EVENT_TYPE_DUPLICATE));
    }

    @Test
    @DisplayName("중복 streamKey 등록 시 EVENT_TYPE_DUPLICATE 예외 발생")
    void register_throwsDuplicate_whenStreamKeyAlreadyExists() {
        // GIVEN
        when(eventTypeRepository.existsByCode("NEW_EVENT")).thenReturn(Mono.just(false));
        when(eventTypeRepository.existsByStreamKey("notif:stream:ticket.opened")).thenReturn(Mono.just(true));

        // WHEN & THEN
        Mono<EventTypeDefinition> result = service.register(
                "NEW_EVENT", "notif:stream:ticket.opened",
                false, true, "memberId", null, 0
        );

        assertThatThrownBy(result::block)
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.EVENT_TYPE_DUPLICATE));
    }

    @Test
    @DisplayName("잘못된 streamKey 형식은 EVENT_TYPE_STREAM_KEY_INVALID 예외 발생")
    void register_throwsInvalidStreamKey_whenFormatWrong() {
        // GIVEN — 패턴 검사는 동기 처리, repository 호출 없음

        // WHEN & THEN
        Mono<EventTypeDefinition> result = service.register(
                "NEW_EVENT", "invalid-key",
                false, true, "memberId", null, 0
        );

        assertThatThrownBy(result::block)
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.EVENT_TYPE_STREAM_KEY_INVALID));
    }
}
