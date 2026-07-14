package com.haagendazs.application.service;

import com.haagendazs.application.dto.ChannelResult;
import com.haagendazs.application.port.SettingCachePort;
import com.haagendazs.common.exception.BusinessException;
import com.haagendazs.domain.exception.NotificationErrorCode;
import com.haagendazs.domain.model.Channel;
import com.haagendazs.domain.model.ChannelType;
import com.haagendazs.domain.repository.ChannelRepository;
import com.haagendazs.domain.repository.SettingEntryRepository;
import com.haagendazs.infrastructure.config.NotificationProperties;
import com.haagendazs.infrastructure.registry.EventTypeRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SettingServiceChannelJunitTest {

    @InjectMocks
    private SettingService settingService;

    @Mock
    private SettingEntryRepository settingEntryRepository;

    @Mock
    private ChannelRepository channelRepository;

    @Mock
    private EventTypeRegistry eventTypeRegistry;

    @Mock
    private NotificationProperties properties;

    @Mock
    private NotificationProperties.Channel channelProperties;

    @Mock
    private SettingCachePort settingCachePort;

    @Test
    @DisplayName("getChannels — 활성 채널 목록을 반환한다")
    void getChannels_returnsEnabledChannels() {
        Channel ch1 = Channel.create(1L, ChannelType.EMAIL, "a@b.com");
        Channel ch2 = Channel.create(1L, ChannelType.EMAIL, "b@b.com");
        when(channelRepository.findByMemberIdAndEnabledTrue(1L)).thenReturn(Flux.just(ch1, ch2));

        List<ChannelResult> results = settingService.getChannels(1L).collectList().block();

        assertThat(results).hasSize(2);
        assertThat(results).extracting(ChannelResult::channelType)
                .containsOnly(ChannelType.EMAIL);
    }

    @Test
    @DisplayName("registerChannel — 중복 채널 타입이면 ALREADY_EXISTS 예외 발생")
    void registerChannel_duplicateType_throwsAlreadyExists() {
        when(channelRepository.existsByMemberIdAndChannelType(1L, ChannelType.EMAIL)).thenReturn(Mono.just(true));

        Mono<ChannelResult> result = settingService.registerChannel(1L, ChannelType.EMAIL, "a@b.com");

        assertThatThrownBy(result::block)
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(NotificationErrorCode.NOTIFICATION_CHANNEL_ALREADY_EXISTS));
    }

    @Test
    @DisplayName("registerChannel — 채널 최대 수 초과 시 LIMIT_EXCEEDED 예외 발생")
    void registerChannel_limitExceeded_throwsLimitExceeded() {
        when(channelRepository.existsByMemberIdAndChannelType(1L, ChannelType.EMAIL)).thenReturn(Mono.just(false));
        when(channelRepository.countByMemberId(1L)).thenReturn(Mono.just(2L));
        when(properties.channel()).thenReturn(channelProperties);
        when(channelProperties.maxPerMember()).thenReturn(2);

        Mono<ChannelResult> result = settingService.registerChannel(1L, ChannelType.EMAIL, "c@b.com");

        assertThatThrownBy(result::block)
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(NotificationErrorCode.NOTIFICATION_CHANNEL_LIMIT_EXCEEDED));
    }

    @Test
    @DisplayName("registerChannel — 정상 등록 시 ChannelResult 반환")
    void registerChannel_success_returnsChannelResult() {
        when(channelRepository.existsByMemberIdAndChannelType(1L, ChannelType.EMAIL)).thenReturn(Mono.just(false));
        when(channelRepository.countByMemberId(1L)).thenReturn(Mono.just(0L));
        when(properties.channel()).thenReturn(channelProperties);
        when(channelProperties.maxPerMember()).thenReturn(2);
        when(channelRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        ChannelResult result = settingService.registerChannel(1L, ChannelType.EMAIL, "a@b.com").block();

        assertThat(result).isNotNull();
        assertThat(result.channelType()).isEqualTo(ChannelType.EMAIL);
        assertThat(result.channelTarget()).isEqualTo("a@b.com");
    }

    @Test
    @DisplayName("deleteChannel — 채널 없으면 NOT_FOUND 예외 발생")
    void deleteChannel_channelNotFound_throwsNotFoundException() {
        when(channelRepository.findById(99L)).thenReturn(Mono.empty());

        Mono<Void> result = settingService.deleteChannel(1L, 99L);

        assertThatThrownBy(result::block)
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(NotificationErrorCode.NOTIFICATION_CHANNEL_NOT_FOUND));
    }

    @Test
    @DisplayName("deleteChannel — 소유자가 맞으면 삭제 성공")
    void deleteChannel_validOwner_deletesChannel() {
        Channel channel = Channel.create(1L, ChannelType.EMAIL, "a@b.com");
        when(channelRepository.findById(10L)).thenReturn(Mono.just(channel));
        when(channelRepository.delete(channel)).thenReturn(Mono.empty());

        settingService.deleteChannel(1L, 10L).block();
    }

    @Test
    @DisplayName("toggleChannel — 채널 없으면 NOT_FOUND 예외 발생")
    void toggleChannel_channelNotFound_throwsNotFoundException() {
        when(channelRepository.findById(99L)).thenReturn(Mono.empty());

        Mono<ChannelResult> result = settingService.toggleChannel(1L, 99L);

        assertThatThrownBy(result::block)
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(NotificationErrorCode.NOTIFICATION_CHANNEL_NOT_FOUND));
    }

    @Test
    @DisplayName("toggleChannel — 소유자 맞으면 enabled 상태가 반전된다")
    void toggleChannel_validOwner_togglesAndReturns() {
        Channel channel = Channel.create(1L, ChannelType.EMAIL, "a@b.com");
        when(channelRepository.findById(10L)).thenReturn(Mono.just(channel));
        when(channelRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        ChannelResult result = settingService.toggleChannel(1L, 10L).block();

        assertThat(result).isNotNull();
        assertThat(result.enabled()).isFalse();
    }
}
