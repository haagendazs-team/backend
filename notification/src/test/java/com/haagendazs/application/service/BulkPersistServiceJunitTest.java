package com.haagendazs.application.service;

import com.haagendazs.application.port.SseNotificationPort;
import com.haagendazs.domain.model.*;
import com.haagendazs.domain.repository.ChannelRepository;
import com.haagendazs.domain.repository.HistoryRepository;
import com.haagendazs.domain.repository.NotificationRepository;
import com.haagendazs.presentation.dto.NotificationResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.stream.RecordId;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BulkPersistServiceJunitTest {

    @InjectMocks
    private BulkPersistService bulkPersistService;

    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private HistoryRepository historyRepository;
    @Mock
    private ChannelRepository channelRepository;
    @Mock
    private SseNotificationPort sseNotificationPort;
    @Mock
    private Dispatcher dispatcher;

    private Event event;
    private Notification notificationMember1;
    private Notification notificationMember2;
    private Channel channelMember1;
    private Channel channelMember2;
    private History successHistory;
    private History failedHistory;

    @BeforeEach
    void setUp() {
        event = Event.withId(1L, "PAYMENT_COMPLETED", "{\"amount\":1000}");

        notificationMember1 = savedNotification(10L, 1L, 1L);
        notificationMember2 = savedNotification(11L, 2L, 1L);

        channelMember1 = channel(1L, ChannelType.EMAIL);
        channelMember2 = channel(2L, ChannelType.EMAIL);

        successHistory = History.sent(10L, ChannelType.EMAIL);
        failedHistory = History.failed(10L, ChannelType.EMAIL, "timeout");
    }

    // ────────── persist() ──────────

    @Test
    @DisplayName("모든 채널 발송이 성공하면 false를 반환한다")
    void persist_allChannelsSent_returnsFalse() {
        // GIVEN
        when(notificationRepository.saveAll(anyList()))
                .thenReturn(Flux.just(notificationMember1, notificationMember2));
        when(channelRepository.findByMemberIdInAndEnabledTrue(any()))
                .thenReturn(Flux.just(channelMember1, channelMember2));
        when(dispatcher.sendToChannelAndBuildHistory(anyLong(), any(), anyString(), anyString()))
                .thenReturn(Mono.just(successHistory));
        when(historyRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        // WHEN
        Boolean result = bulkPersistService.persist(event, List.of(notificationMember1, notificationMember2), "payload").block();

        // THEN
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("하나라도 발송 실패 History가 있으면 true를 반환한다")
    void persist_anyChannelFailed_returnsTrue() {
        // GIVEN
        when(notificationRepository.saveAll(anyList()))
                .thenReturn(Flux.just(notificationMember1));
        when(channelRepository.findByMemberIdInAndEnabledTrue(any()))
                .thenReturn(Flux.just(channelMember1));
        when(dispatcher.sendToChannelAndBuildHistory(anyLong(), any(), anyString(), anyString()))
                .thenReturn(Mono.just(failedHistory));
        when(historyRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        // WHEN
        Boolean result = bulkPersistService.persist(event, List.of(notificationMember1), "payload").block();

        // THEN
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("활성 채널이 없는 멤버는 dispatcher를 호출하지 않고 false를 반환한다")
    void persist_noChannelForMember_returnsFalse() {
        // GIVEN
        when(notificationRepository.saveAll(anyList()))
                .thenReturn(Flux.just(notificationMember1));
        when(channelRepository.findByMemberIdInAndEnabledTrue(any()))
                .thenReturn(Flux.empty());

        // WHEN
        Boolean result = bulkPersistService.persist(event, List.of(notificationMember1), "payload").block();

        // THEN
        assertThat(result).isFalse();
        verify(dispatcher, never()).sendToChannelAndBuildHistory(anyLong(), any(), anyString(), anyString());
    }

    @Test
    @DisplayName("persist는 saved의 ID(DB 발급)를 dispatcher에 전달한다")
    void persist_passesDbIssuedNotificationIdToDispatcher() {
        // GIVEN
        when(notificationRepository.saveAll(anyList()))
                .thenReturn(Flux.just(notificationMember1));
        when(channelRepository.findByMemberIdInAndEnabledTrue(any()))
                .thenReturn(Flux.just(channelMember1));
        when(dispatcher.sendToChannelAndBuildHistory(anyLong(), any(), anyString(), anyString()))
                .thenReturn(Mono.just(successHistory));
        when(historyRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        // WHEN
        bulkPersistService.persist(event, List.of(notificationMember1), "payload").block();

        // THEN
        verify(dispatcher).sendToChannelAndBuildHistory(eq(10L), any(), anyString(), anyString());
    }

    // ────────── persistBuffered() ──────────

    @Test
    @DisplayName("persistBuffered는 모든 발송 완료 후 SSE를 발송한다")
    void persistBuffered_allSent_sendsSse() {
        // GIVEN
        BufferItem item = bufferItem(notificationMember1, "PAYMENT_COMPLETED", "payload1");
        when(notificationRepository.saveAll(anyList()))
                .thenReturn(Flux.just(notificationMember1));
        when(channelRepository.findByMemberIdInAndEnabledTrue(any()))
                .thenReturn(Flux.just(channelMember1));
        when(dispatcher.sendToChannelAndBuildHistory(anyLong(), any(), anyString(), anyString()))
                .thenReturn(Mono.just(successHistory));
        when(historyRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        // WHEN
        bulkPersistService.persistBuffered(List.of(item)).block();

        // THEN
        verify(sseNotificationPort).send(eq(1L), any(NotificationResponse.class));
    }

    @Test
    @DisplayName("persistBuffered는 채널 발송 실패해도 SSE는 발송한다 — all-complete, short-circuit 없음")
    void persistBuffered_channelFailed_stillSendsSse() {
        // GIVEN
        BufferItem item = bufferItem(notificationMember1, "PAYMENT_COMPLETED", "payload1");
        when(notificationRepository.saveAll(anyList()))
                .thenReturn(Flux.just(notificationMember1));
        when(channelRepository.findByMemberIdInAndEnabledTrue(any()))
                .thenReturn(Flux.just(channelMember1));
        when(dispatcher.sendToChannelAndBuildHistory(anyLong(), any(), anyString(), anyString()))
                .thenReturn(Mono.just(failedHistory));
        when(historyRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        // WHEN
        bulkPersistService.persistBuffered(List.of(item)).block();

        // THEN
        verify(sseNotificationPort).send(eq(1L), any(NotificationResponse.class));
    }

    @Test
    @DisplayName("persistBuffered는 채널이 없어도 SSE는 발송한다")
    void persistBuffered_noChannel_stillSendsSse() {
        // GIVEN
        BufferItem item = bufferItem(notificationMember1, "PAYMENT_COMPLETED", "payload1");
        when(notificationRepository.saveAll(anyList()))
                .thenReturn(Flux.just(notificationMember1));
        when(channelRepository.findByMemberIdInAndEnabledTrue(any()))
                .thenReturn(Flux.empty());

        // WHEN
        bulkPersistService.persistBuffered(List.of(item)).block();

        // THEN
        verify(sseNotificationPort).send(eq(1L), any(NotificationResponse.class));
        verify(dispatcher, never()).sendToChannelAndBuildHistory(anyLong(), any(), anyString(), anyString());
    }

    @Test
    @DisplayName("persistBuffered는 각 BufferItem의 subject/payload를 해당 notification에 매핑하여 dispatcher에 전달한다")
    void persistBuffered_routesSubjectAndPayloadPerMember() {
        // GIVEN
        BufferItem item1 = bufferItem(notificationMember1, "CHAT_MENTION", "chat-payload");
        BufferItem item2 = bufferItem(notificationMember2, "PAYMENT_COMPLETED", "pay-payload");
        when(notificationRepository.saveAll(anyList()))
                .thenReturn(Flux.just(notificationMember1, notificationMember2));
        when(channelRepository.findByMemberIdInAndEnabledTrue(any()))
                .thenReturn(Flux.just(channelMember1, channelMember2));
        when(dispatcher.sendToChannelAndBuildHistory(anyLong(), any(), anyString(), anyString()))
                .thenReturn(Mono.just(successHistory));
        when(historyRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        // WHEN
        bulkPersistService.persistBuffered(List.of(item1, item2)).block();

        // THEN
        verify(dispatcher).sendToChannelAndBuildHistory(eq(10L), any(), eq("CHAT_MENTION"), eq("chat-payload"));
        verify(dispatcher).sendToChannelAndBuildHistory(eq(11L), any(), eq("PAYMENT_COMPLETED"), eq("pay-payload"));
    }

    @Test
    @DisplayName("persistBuffered SSE 발송 예외는 삼키고 다른 멤버 처리에 영향을 주지 않는다")
    void persistBuffered_sseFails_doesNotPropagateError() {
        // GIVEN
        BufferItem item = bufferItem(notificationMember1, "PAYMENT_COMPLETED", "payload1");
        when(notificationRepository.saveAll(anyList()))
                .thenReturn(Flux.just(notificationMember1));
        when(channelRepository.findByMemberIdInAndEnabledTrue(any()))
                .thenReturn(Flux.empty());
        doThrow(new RuntimeException("SSE 연결 끊김"))
                .when(sseNotificationPort).send(anyLong(), any());

        // WHEN / THEN — 예외가 전파되지 않고 정상 완료
        bulkPersistService.persistBuffered(List.of(item)).block();
    }

    // ────────── helpers ──────────

    private static Notification savedNotification(Long id, Long memberId, Long eventId) {
        return Notification.withId(id, memberId, eventId);
    }

    private static Channel channel(Long memberId, ChannelType type) {
        return Channel.create(memberId, type, "target@example.com");
    }

    private static BufferItem bufferItem(Notification notification, String subject, String payload) {
        return BufferItem.of(notification, subject, payload, "stream:key", RecordId.of("0-0"));
    }
}
