package com.haagendazs.application.service;

import com.haagendazs.application.port.SseNotificationPort;
import com.haagendazs.domain.model.BufferItem;
import com.haagendazs.domain.model.Notification;
import com.haagendazs.domain.model.Event;
import com.haagendazs.domain.repository.NotificationRepository;
import com.haagendazs.domain.repository.SettingEntryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class BufferedChunkService {

    private final NotificationRepository notificationRepository;
    private final SettingEntryRepository settingEntryRepository;
    private final SseNotificationPort sseNotificationPort;
    private final NotificationBatchBuffer buffer;
    private final BulkPersistService bulkPersistService;

    public Mono<Void> enqueueChunk(Event event, List<Long> memberIds, String payload,
                                    String streamKey, RecordId recordId) {
        if (memberIds.isEmpty()) {
            return Mono.empty();
        }

        return notificationRepository.findExistingMemberIdsByEventId(event.getId(), memberIds)
                .flatMap(alreadyNotified -> settingEntryRepository.findDisabledMemberIdsByEventTypeCode(memberIds, event.getEventTypeCode())
                                .collect(Collectors.toSet())
                                .flatMap(disabledIds -> buildAndEnqueue(
                                        event, memberIds, payload, streamKey, recordId, alreadyNotified, disabledIds)));
    }

    private Mono<Void> buildAndEnqueue(Event event, List<Long> memberIds, String payload,
                                        String streamKey, RecordId recordId,
                                        Set<Long> alreadyNotified, Set<Long> disabledIds) {
        List<Long> candidates = memberIds.stream()
                .filter(id -> !alreadyNotified.contains(id) && !disabledIds.contains(id))
                .toList();

        if (candidates.isEmpty()) {
            return Mono.empty();
        }

        return Flux.fromIterable(candidates)
                .flatMap(memberId -> sseNotificationPort.isConnected(memberId)
                        .map(connected -> new MemberConnectionState(memberId, connected)), 32)
                .collectList()
                .flatMap(states -> {
                    List<Notification> ssePending = states.stream()
                            .filter(MemberConnectionState::connected)
                            .map(s -> Notification.create(s.memberId(), event.getId()))
                            .toList();

                    List<Notification> directPersist = states.stream()
                            .filter(s -> !s.connected())
                            .map(s -> Notification.create(s.memberId(), event.getId()))
                            .toList();

                    ssePending.forEach(notification ->
                            buffer.enqueue(BufferItem.of(notification, event.getTypeName(), payload, streamKey, recordId)));

                    return directPersist.isEmpty()
                            ? Mono.empty()
                            : bulkPersistService.persist(event, directPersist, payload).then();
                });
    }

    private record MemberConnectionState(Long memberId, boolean connected) {}
}
