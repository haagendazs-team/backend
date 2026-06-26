package com.haagendazs.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class SettingEntryJunitTest {

    @Test
    @DisplayName("SettingEntry 생성 시 기본값 is_enabled=true")
    void create_defaultEnabled() {
        SettingEntry entry = SettingEntry.create(1L, "TICKET_OPEN");

        assertThat(entry.getMemberId()).isEqualTo(1L);
        assertThat(entry.getEventTypeCode()).isEqualTo("TICKET_OPEN");
        assertThat(entry.isEnabled()).isTrue();
    }
}
