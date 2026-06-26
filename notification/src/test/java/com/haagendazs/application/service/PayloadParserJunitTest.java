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
}
