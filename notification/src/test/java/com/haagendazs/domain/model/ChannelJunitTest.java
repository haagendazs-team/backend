package com.haagendazs.domain.model;

import com.haagendazs.common.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChannelJunitTest {

    @Test
    @DisplayName("Channel.create 시 enabled=true로 초기화된다")
    void create_initializedEnabledTrue() {
        Channel channel = Channel.create(1L, ChannelType.EMAIL, "test@example.com");

        assertThat(channel.getMemberId()).isEqualTo(1L);
        assertThat(channel.getChannelType()).isEqualTo(ChannelType.EMAIL);
        assertThat(channel.getChannelTarget()).isEqualTo("test@example.com");
        assertThat(channel.isEnabled()).isTrue();
    }

    @Test
    @DisplayName("toggle 호출 시 enabled 상태가 반전된다")
    void toggle_revertsEnabledState() {
        Channel channel = Channel.create(1L, ChannelType.EMAIL, "test@example.com");
        assertThat(channel.isEnabled()).isTrue();

        channel.toggle();
        assertThat(channel.isEnabled()).isFalse();

        channel.toggle();
        assertThat(channel.isEnabled()).isTrue();
    }

    @Test
    @DisplayName("validateOwner — 동일 memberId면 예외 없음")
    void validateOwner_sameOwner_noException() {
        Channel channel = Channel.create(1L, ChannelType.EMAIL, "test@example.com");
        channel.validateOwner(1L);
    }

    @Test
    @DisplayName("validateOwner — 다른 memberId면 BusinessException 발생")
    void validateOwner_differentOwner_throwsBusinessException() {
        Channel channel = Channel.create(1L, ChannelType.EMAIL, "test@example.com");

        assertThatThrownBy(() -> channel.validateOwner(99L))
                .isInstanceOf(BusinessException.class);
    }
}
