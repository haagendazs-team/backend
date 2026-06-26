package com.haagendazs.application.service;

import com.haagendazs.application.port.SseNotificationPort;
import com.haagendazs.domain.model.BufferItem;
import com.haagendazs.domain.model.Notification;
import com.haagendazs.domain.model.Event;
import com.haagendazs.domain.repository.NotificationRepository;
import com.haagendazs.domain.repository.SettingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class BufferedChunkService {

    private final NotificationRepository notificationRepository;
    private final SettingRepository settingRepository;
    private final SseNotificationPort sseNotificationPort;
    private final NotificationBatchBuffer buffer;
    private final BulkPersistService bulkPersistService;

    public Mono<Void> enqueueChunk(Event event, List<Long> memberIds, String payload,
                                    String streamKey, RecordId recordId) {
        if (memberIds.isEmpty()) {
            return Mono.empty();
        }

        return notificationRepository.findExistingMemberIdsByEventId(event.getId(), memberIds)
                .flatMap(alreadyNotified -> settingRepository.findByMemberId(memberIds.get(0))
                        .flux()
                        .mergeWith(Flux.fromIterable(memberIds.subList(1, memberIds.size()))
                                .flatMap(id -> settingRepository.findByMemberId(id)))
                        .collectMap(s -> s.getMemberId())
                        .flatMap(settingMap -> {
                            List<Notification> ssePending = new java.util.ArrayList<>();
                            List<Notification> directPersist = new java.util.ArrayList<>();

                            for (Long memberId : memberIds) {
                                if (alreadyNotified.contains(memberId)) {
                                    continue;
                                }
                                var setting = settingMap.get(memberId);
                                if (setting != null && !setting.isEnabledFor(event.getEventTypeCode())) {
                                    continue;
                                }
                                Notification notification = Notification.create(memberId, event.getId());
                                if (sseNotificationPort.isConnected(memberId)) {
                                    ssePending.add(notification);
                                } else {
                                    directPersist.add(notification);
                                }
                            }

                            Mono<Void> persistMono = directPersist.isEmpty()
                                    ? Mono.empty()
                                    : bulkPersistService.persist(event, directPersist, payload).then();

                            ssePending.forEach(notification ->
                                    buffer.enqueue(BufferItem.of(
                                            notification, event.getTypeName(), payload, streamKey, recordId)));

                            return persistMono;
                        }));
    }
}
