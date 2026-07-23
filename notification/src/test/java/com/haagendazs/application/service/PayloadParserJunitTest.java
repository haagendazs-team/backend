package com.haagendazs.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.haagendazs.domain.model.NotificationEnvelope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PayloadParserJunitTest {

    private PayloadParser payloadParser;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        payloadParser = new PayloadParser(objectMapper);
    }

    @Test
    @DisplayName("정상 envelope JSON을 파싱한다")
    void parse_validEnvelope_returnsEnvelope() {
        String json = """
                {"memberId":1,"isDispatchType":"IMMEDIATE","scheduledAt":null,"payload":{}}
                """;

        NotificationEnvelope result = payloadParser.parse(json);

        assertThat(result.memberId()).isEqualTo(1L);
        assertThat(result.isDispatchType()).isEqualTo(NotificationEnvelope.DispatchType.IMMEDIATE);
    }

    @Test
    @DisplayName("raw JSON에서 eventTypeCode를 추출한다")
    void extractEventTypeCode_returnsCode_whenPresent() {
        String json = """
                {"eventTypeCode":"PAYMENT_COMPLETED","memberId":1,"isDispatchType":"IMMEDIATE","payload":{}}
                """;

        assertThat(payloadParser.extractEventTypeCode(json)).isEqualTo("PAYMENT_COMPLETED");
    }

    @Test
    @DisplayName("eventTypeCode 누락 시 IllegalArgumentException 발생")
    void extractEventTypeCode_throwsWhenMissing() {
        String json = """
                {"memberId":1,"isDispatchType":"IMMEDIATE","payload":{}}
                """;

        assertThatThrownBy(() -> payloadParser.extractEventTypeCode(json))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("잘못된 JSON이면 IllegalArgumentException 발생")
    void parse_invalidJson_throwsException() {
        assertThatThrownBy(() -> payloadParser.parse("not-json"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("memberId가 있으면 extractTargetMemberId 정상 반환")
    void extractTargetMemberId_returnsValue_whenPresent() {
        NotificationEnvelope envelope = NotificationEnvelope.of(
                99L, NotificationEnvelope.DispatchType.IMMEDIATE, null, null);

        assertThat(payloadParser.extractTargetMemberId(envelope)).isEqualTo(99L);
    }

    @Test
    @DisplayName("memberId가 null이면 IllegalArgumentException 발생")
    void extractTargetMemberId_throwsWhenNull() {
        NotificationEnvelope envelope = NotificationEnvelope.of(
                null, NotificationEnvelope.DispatchType.IMMEDIATE, null, null);

        assertThatThrownBy(() -> payloadParser.extractTargetMemberId(envelope))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("IMMEDIATE dispatch이면 빈 Optional 반환")
    void extractScheduledAt_returnsEmpty_whenImmediate() {
        NotificationEnvelope envelope = NotificationEnvelope.of(
                1L, NotificationEnvelope.DispatchType.IMMEDIATE, null, null);

        assertThat(payloadParser.extractScheduledAt(envelope)).isEmpty();
    }

    @Test
    @DisplayName("SCHEDULED이고 미래 시각이면 Optional 반환")
    void extractScheduledAt_returnsFutureTime_whenScheduled() {
        LocalDateTime future = LocalDateTime.now().plusHours(2);
        NotificationEnvelope envelope = NotificationEnvelope.of(
                1L, NotificationEnvelope.DispatchType.SCHEDULED, future, null);

        Optional<LocalDateTime> result = payloadParser.extractScheduledAt(envelope);

        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(future);
    }

    @Test
    @DisplayName("SCHEDULED이지만 과거 시각이면 빈 Optional 반환")
    void extractScheduledAt_returnsEmpty_whenPastTime() {
        LocalDateTime past = LocalDateTime.now().minusHours(1);
        NotificationEnvelope envelope = NotificationEnvelope.of(
                1L, NotificationEnvelope.DispatchType.SCHEDULED, past, null);

        assertThat(payloadParser.extractScheduledAt(envelope)).isEmpty();
    }

    @Test
    @DisplayName("extractEventTypeCode 호출 시 JSON 파싱 자체가 실패하면 IllegalArgumentException 발생")
    void extractEventTypeCode_throwsWhenJsonUnparseable() {
        assertThatThrownBy(() -> payloadParser.extractEventTypeCode("{invalid"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
