package com.haagendazs.presentation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.haagendazs.domain.model.PingStatus;

import java.time.LocalDateTime;
import java.util.List;

public record PingResultResponse(
        @JsonProperty("총전송받은횟수") int totalSent,
        @JsonProperty("성공적으로받은횟수") int successCount,
        @JsonProperty("시간들") List<PingEntry> entries
) {
    public record PingEntry(
            @JsonProperty("date") LocalDateTime date,
            @JsonProperty("전송상태") PingStatus status
    ) {}
}
