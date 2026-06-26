package com.haagendazs.application.service;

import com.haagendazs.common.exception.BusinessException;
import com.haagendazs.common.exception.ErrorCode;
import com.haagendazs.application.dto.ChannelResult;
import com.haagendazs.application.dto.SettingResult;
import com.haagendazs.application.dto.UpdateSettingCommand;
import com.haagendazs.domain.model.Channel;
import com.haagendazs.domain.model.ChannelType;
import com.haagendazs.domain.model.SettingEntry;
import com.haagendazs.domain.repository.ChannelRepository;
import com.haagendazs.domain.repository.SettingEntryRepository;
import com.haagendazs.infrastructure.config.NotificationProperties;
import com.haagendazs.infrastructure.registry.EventTypeRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class SettingService {

    private final SettingEntryRepository settingEntryRepository;
    private final ChannelRepository channelRepository;
    private final EventTypeRegistry eventTypeRegistry;
    private final NotificationProperties properties;

    @Transactional(readOnly = true)
    public Flux<SettingResult> getSettings(Long memberId) {
        return Flux.fromIterable(eventTypeRegistry.getAllDefinitions())
                .filter(def -> def.isEnabled())
                .flatMap(def -> settingEntryRepository.findByMemberIdAndEventTypeCode(memberId, def.getCode())
                        .map(SettingResult::from)
                        .switchIfEmpty(Mono.just(new SettingResult(memberId, def.getCode(), true))));
    }

    @Transactional
    public Mono<SettingResult> updateSetting(Long memberId, UpdateSettingCommand command) {
        if (eventTypeRegistry.getByCode(command.eventTypeCode()).isEmpty()) {
            return Mono.error(new BusinessException(ErrorCode.EVENT_TYPE_NOT_FOUND));
        }
        return settingEntryRepository.findByMemberIdAndEventTypeCode(memberId, command.eventTypeCode())
                .switchIfEmpty(Mono.defer(() ->
                        settingEntryRepository.save(SettingEntry.create(memberId, command.eventTypeCode()))))
                .flatMap(entry -> {
                    entry.updateEnabled(command.enabled());
                    return settingEntryRepository.save(entry);
                })
                .map(SettingResult::from);
    }

    @Transactional(readOnly = true)
    public Flux<ChannelResult> getChannels(Long memberId) {
        return channelRepository.findByMemberIdAndEnabledTrue(memberId)
                .map(ChannelResult::from);
    }

    @Transactional
    public Mono<ChannelResult> registerChannel(Long memberId, ChannelType channelType, String channelTarget) {
        return channelRepository.existsByMemberIdAndChannelType(memberId, channelType)
                .flatMap(exists -> {
                    if (exists) {
                        return Mono.error(new BusinessException(ErrorCode.NOTIFICATION_CHANNEL_ALREADY_EXISTS));
                    }
                    return channelRepository.countByMemberId(memberId);
                })
                .flatMap(count -> {
                    if (count >= properties.channel().maxPerMember()) {
                        return Mono.error(new BusinessException(ErrorCode.NOTIFICATION_CHANNEL_LIMIT_EXCEEDED));
                    }
                    return channelRepository.save(Channel.create(memberId, channelType, channelTarget));
                })
                .map(ChannelResult::from)
                .onErrorMap(DataIntegrityViolationException.class,
                        e -> new BusinessException(ErrorCode.NOTIFICATION_CHANNEL_ALREADY_EXISTS));
    }

    @Transactional
    public Mono<Void> deleteChannel(Long memberId, Long channelId) {
        return channelRepository.findById(channelId)
                .switchIfEmpty(Mono.error(new BusinessException(ErrorCode.NOTIFICATION_CHANNEL_NOT_FOUND)))
                .flatMap(channel -> {
                    channel.validateOwner(memberId);
                    return channelRepository.delete(channel);
                });
    }

    @Transactional
    public Mono<ChannelResult> toggleChannel(Long memberId, Long channelId) {
        return channelRepository.findById(channelId)
                .switchIfEmpty(Mono.error(new BusinessException(ErrorCode.NOTIFICATION_CHANNEL_NOT_FOUND)))
                .flatMap(channel -> {
                    channel.validateOwner(memberId);
                    channel.toggle();
                    return channelRepository.save(channel);
                })
                .map(ChannelResult::from);
    }
}
