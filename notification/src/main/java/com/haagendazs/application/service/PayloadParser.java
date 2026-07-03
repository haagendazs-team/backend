package com.haagendazs.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.haagendazs.domain.model.NotificationEnvelope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class PayloadParser {

    private final ObjectMapper objectMapper;

    public NotificationEnvelope parse(String rawPayload) {
        try {
            return objectMapper.readValue(rawPayload, NotificationEnvelope.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("envelope 파싱 실패 payload=" + rawPayload, e);
        }
    }

    public String extractEventTypeCode(String rawPayload) {
        try {
            JsonNode node = objectMapper.readTree(rawPayload);
            JsonNode codeNode = node.get("eventTypeCode");
            if (codeNode == null || codeNode.isNull()) {
                throw new IllegalArgumentException("eventTypeCode 누락 payload=" + rawPayload);
            }
            return codeNode.asText();
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("eventTypeCode 추출 실패 payload=" + rawPayload, e);
        }
    }

    public Long extractTargetMemberId(NotificationEnvelope envelope) {
        Long memberId = envelope.memberId();
        if (memberId == null) {
            throw new IllegalArgumentException("memberId 누락");
        }
        return memberId;
    }

    public Optional<LocalDateTime> extractScheduledAt(NotificationEnvelope envelope) {
        if (!envelope.isScheduled()) {
            return Optional.empty();
        }
        LocalDateTime scheduledAt = envelope.scheduledAt();
        if (scheduledAt.isBefore(LocalDateTime.now())) {
            return Optional.empty();
        }
        return Optional.of(scheduledAt);
    }
}
