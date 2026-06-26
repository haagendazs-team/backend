package com.haagendazs.infrastructure.repository;

import com.haagendazs.domain.model.Channel;
import com.haagendazs.domain.model.ChannelType;
import com.haagendazs.domain.repository.ChannelRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class ChannelRepositoryAdapter implements ChannelRepository {

    private final ChannelR2dbcRepository r2dbcRepository;

    @Override
    public Mono<Channel> save(Channel channel) {
        return r2dbcRepository.save(channel);
    }

    @Override
    public Mono<Channel> findById(Long id) {
        return r2dbcRepository.findById(id);
    }

    @Override
    public Flux<Channel> findByMemberIdAndEnabledTrue(Long memberId) {
        return r2dbcRepository.findByMemberIdAndEnabledTrue(memberId);
    }

    @Override
    public Flux<Channel> findByMemberIdInAndEnabledTrue(List<Long> memberIds) {
        return r2dbcRepository.findByMemberIdInAndEnabledTrue(memberIds.toArray(Long[]::new));
    }

    @Override
    public Mono<Boolean> existsByMemberIdAndChannelType(Long memberId, ChannelType channelType) {
        return r2dbcRepository.existsByMemberIdAndChannelType(memberId, channelType);
    }

    @Override
    public Mono<Long> countByMemberId(Long memberId) {
        return r2dbcRepository.countByMemberId(memberId);
    }

    @Override
    public Mono<Void> delete(Channel channel) {
        return r2dbcRepository.delete(channel);
    }
}
