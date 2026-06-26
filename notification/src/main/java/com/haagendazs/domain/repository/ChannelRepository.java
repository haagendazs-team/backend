package com.haagendazs.domain.repository;

import com.haagendazs.domain.model.Channel;
import com.haagendazs.domain.model.ChannelType;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

public interface ChannelRepository {
    Mono<Channel> save(Channel channel);
    Mono<Channel> findById(Long id);
    Flux<Channel> findByMemberIdAndEnabledTrue(Long memberId);
    Flux<Channel> findByMemberIdInAndEnabledTrue(List<Long> memberIds);
    Mono<Boolean> existsByMemberIdAndChannelType(Long memberId, ChannelType channelType);
    Mono<Long> countByMemberId(Long memberId);
    Mono<Void> delete(Channel channel);
}
