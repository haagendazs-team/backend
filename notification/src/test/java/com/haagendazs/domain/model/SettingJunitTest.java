package com.haagendazs.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SettingJunitTest {

    @Test
    @DisplayName("createDefault 시 모든 알림이 enabled=true로 초기화된다")
    void createDefault_allAlertsEnabled() {
        Setting setting = Setting.createDefault(1L);

        assertThat(setting.getMemberId()).isEqualTo(1L);
        assertThat(setting.isEnabledFor("TICKET_OPEN")).isTrue();
        assertThat(setting.isEnabledFor("GAME_START")).isTrue();
        assertThat(setting.isEnabledFor("PAYMENT_COMPLETED")).isTrue();
        assertThat(setting.isEnabledFor("CHAT_MENTION")).isTrue();
        assertThat(setting.isEnabledFor("CHAT_INVITED")).isTrue();
    }

    @Test
    @DisplayName("isEnabledFor — 알 수 없는 이벤트 코드는 true 반환 (기본값)")
    void isEnabledFor_unknownCode_returnsTrue() {
        Setting setting = Setting.createDefault(1L);

        assertThat(setting.isEnabledFor("UNKNOWN_EVENT")).isTrue();
    }

    @Test
    @DisplayName("update 호출 후 변경된 값이 반영된다")
    void update_appliesChanges() {
        Setting setting = Setting.createDefault(1L);

        setting.update(false, true, false, true);

        assertThat(setting.isEnabledFor("TICKET_OPEN")).isFalse();
        assertThat(setting.isEnabledFor("GAME_START")).isTrue();
        assertThat(setting.isEnabledFor("PAYMENT_COMPLETED")).isFalse();
        assertThat(setting.isEnabledFor("CHAT_MENTION")).isTrue();
    }
}
