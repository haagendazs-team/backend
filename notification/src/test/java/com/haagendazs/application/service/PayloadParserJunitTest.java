package com.haagendazs.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.haagendazs.domain.model.EventTypeDefinition;
import com.haagendazs.infrastructure.config.NotificationProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class PayloadParserJunitTest {

    @InjectMocks
    private PayloadParser payloadParser;

    @Mock
    private NotificationProperties properties;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("memberIdField 경로로 memberId를 추출한다")
    void extractMemberId_usesDefinitionField() throws Exception {
        PayloadParser parser = new PayloadParser(objectMapper, properties);
        EventTypeDefinition def = EventTypeDefinition.of(
                "PAYMENT_COMPLETED", "notif:stream:payment.completed",
                false, true, "memberId", null, 0);
        String payload = "{\"memberId\":42,\"amount\":1000}";

        Long result = parser.extractMemberId(payload, def);

        assertThat(result).isEqualTo(42L);
    }

    @Test
    @DisplayName("memberId 필드 없으면 IllegalArgumentException 발생")
    void extractMemberId_throwsWhenFieldMissing() {
        PayloadParser parser = new PayloadParser(objectMapper, properties);
        EventTypeDefinition def = EventTypeDefinition.of(
                "PAYMENT_COMPLETED", "notif:stream:payment.completed",
                false, true, "memberId", null, 0);
        String payload = "{\"amount\":1000}";

        assertThatThrownBy(() -> parser.extractMemberId(payload, def))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("scheduledAtField가 null이면 빈 Optional 반환")
    void extractScheduledAt_returnsEmpty_whenNoScheduledAtField() {
        PayloadParser parser = new PayloadParser(objectMapper, properties);
        EventTypeDefinition def = EventTypeDefinition.of(
                "PAYMENT_COMPLETED", "notif:stream:payment.completed",
                false, true, "memberId", null, 0);

        Optional<LocalDateTime> result = parser.extractScheduledAt("{}", def);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("memberId 필드 값이 null이면 IllegalArgumentException 발생")
    void extractMemberId_throwsWhenFieldNull() {
        PayloadParser parser = new PayloadParser(objectMapper, properties);
        EventTypeDefinition def = EventTypeDefinition.of(
                "TICKET_OPEN", "notif:stream:ticket.open",
                false, true, "memberId", null, 0);
        String payload = "{\"memberId\":null}";

        assertThatThrownBy(() -> parser.extractMemberId(payload, def))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("memberId 필드 값이 문자열이면 IllegalArgumentException 발생")
    void extractMemberId_throwsWhenFieldNotIntegral() {
        PayloadParser parser = new PayloadParser(objectMapper, properties);
        EventTypeDefinition def = EventTypeDefinition.of(
                "TICKET_OPEN", "notif:stream:ticket.open",
                false, true, "memberId", null, 0);
        String payload = "{\"memberId\":\"abc\"}";

        assertThatThrownBy(() -> parser.extractMemberId(payload, def))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("scheduledAtField가 있고 유효한 미래 시각이면 Optional 반환")
    void extractScheduledAt_withValidFutureTime_returnsOptional() {
        PayloadParser parser = new PayloadParser(objectMapper, properties);
        LocalDateTime future = LocalDateTime.now().plusHours(2);
        EventTypeDefinition def = EventTypeDefinition.of(
                "GAME_START", "notif:stream:game.start",
                false, true, "memberId", "scheduledAt", 30);
        String payload = "{\"memberId\":1,\"scheduledAt\":\"" + future + "\"}";

        Optional<LocalDateTime> result = parser.extractScheduledAt(payload, def);

        assertThat(result).isPresent();
    }

    @Test
    @DisplayName("scheduledAtField 있지만 필드 누락 시 빈 Optional 반환")
    void extractScheduledAt_withMissingField_returnsEmpty() {
        PayloadParser parser = new PayloadParser(objectMapper, properties);
        EventTypeDefinition def = EventTypeDefinition.of(
                "GAME_START", "notif:stream:game.start",
                false, true, "memberId", "scheduledAt", 0);
        String payload = "{\"memberId\":1}";

        Optional<LocalDateTime> result = parser.extractScheduledAt(payload, def);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("잘못된 JSON 페이로드 시 IllegalArgumentException 발생")
    void extractMemberId_throwsWhenInvalidJson() {
        PayloadParser parser = new PayloadParser(objectMapper, properties);
        EventTypeDefinition def = EventTypeDefinition.of(
                "TICKET_OPEN", "notif:stream:ticket.open",
                false, true, "memberId", null, 0);

        assertThatThrownBy(() -> parser.extractMemberId("not-json", def))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
