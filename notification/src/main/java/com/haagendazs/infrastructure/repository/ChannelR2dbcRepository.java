package com.haagendazs.infrastructure.repository;

import com.haagendazs.domain.model.Channel;
import com.haagendazs.domain.model.ChannelType;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

public interface ChannelR2dbcRepository extends ReactiveCrudRepository<Channel, Long> {

    Flux<Channel> findByMemberIdAndEnabledTrue(Long memberId);

    @Query("SELECT * FROM notification.channels WHERE member_id = ANY(:memberIds) AND is_enabled = true")
    Flux<Channel> findByMemberIdInAndEnabledTrue(Long[] memberIds);

    Mono<Boolean> existsByMemberIdAndChannelType(Long memberId, ChannelType channelType);

    @Query("SELECT COUNT(*) FROM notification.channels WHERE member_id = :memberId")
    Mono<Long> countByMemberId(Long memberId);
}
