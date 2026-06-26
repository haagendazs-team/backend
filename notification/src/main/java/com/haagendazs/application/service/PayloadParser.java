package com.haagendazs.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.haagendazs.domain.model.EventTypeDefinition;
import com.haagendazs.infrastructure.config.NotificationProperties;
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
    private final NotificationProperties properties;

    public Long extractMemberId(String payload, EventTypeDefinition definition) {
        try {
            JsonNode node = objectMapper.readTree(payload);
            JsonNode memberIdNode = node.get(definition.getMemberIdField());
            if (memberIdNode == null || memberIdNode.isNull() || !memberIdNode.isIntegralNumber()) {
                throw new IllegalArgumentException(
                        "invalid " + definition.getMemberIdField() + " in " + definition.getCode() + " payload");
            }
            return memberIdNode.longValue();
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "invalid memberId in " + definition.getCode() + " payload", e);
        }
    }

    public Optional<LocalDateTime> extractScheduledAt(String payload, EventTypeDefinition definition) {
        if (definition.getScheduledAtField() == null) {
            return Optional.empty();
        }
        try {
            JsonNode node = objectMapper.readTree(payload);
            LocalDateTime now = LocalDateTime.now();
            return parseDateTime(node, definition.getScheduledAtField())
                    .map(t -> t.minusMinutes(definition.getScheduledOffsetMinutes()))
                    .filter(t -> t.isAfter(now));
        } catch (Exception e) {
            log.warn("scheduledAt 파싱 실패 eventType={} payload={}", definition.getCode(), payload, e);
            return Optional.empty();
        }
    }

    private Optional<LocalDateTime> parseDateTime(JsonNode node, String fieldName) {
        JsonNode fieldNode = node.get(fieldName);
        if (fieldNode == null || fieldNode.isNull()) {
            return Optional.empty();
        }
        return Optional.of(LocalDateTime.parse(fieldNode.asText()));
    }
}
