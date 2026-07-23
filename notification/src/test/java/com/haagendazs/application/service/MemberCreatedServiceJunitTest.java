package com.haagendazs.application.service;

import com.haagendazs.domain.model.Channel;
import com.haagendazs.domain.model.ChannelType;
import com.haagendazs.domain.model.EventTypeDefinition;
import com.haagendazs.domain.model.SettingEntry;
import com.haagendazs.domain.repository.ChannelRepository;
import com.haagendazs.domain.repository.SettingEntryRepository;
import com.haagendazs.infrastructure.registry.EventTypeRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MemberCreatedServiceJunitTest {

    @Mock
    private ChannelRepository channelRepository;

    @Mock
    private SettingEntryRepository settingEntryRepository;

    @Mock
    private EventTypeRegistry eventTypeRegistry;

    private MemberCreatedService memberCreatedService;

    @BeforeEach
    void setUp() {
        memberCreatedService = new MemberCreatedService(channelRepository, settingEntryRepository, eventTypeRegistry);
    }

    @Test
    @DisplayName("회원 생성 시 EMAIL 채널이 초기화된다")
    void initialize_createsEmailChannel() {
        // given
        Long memberId = 1L;
        String email = "user@example.com";
        EventTypeDefinition definition = mock(EventTypeDefinition.class);
        when(definition.getCode()).thenReturn("push.v1");
        when(definition.isEnabled()).thenReturn(true);
        when(eventTypeRegistry.getAllDefinitions()).thenReturn(List.of(definition));
        when(channelRepository.save(any(Channel.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(settingEntryRepository.findByMemberIdAndEventTypeCode(memberId, "push.v1")).thenReturn(Mono.empty());
        when(settingEntryRepository.save(any(SettingEntry.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        // when
        memberCreatedService.initialize(memberId, email).block();

        // then — SSE 제거 후 EMAIL 채널 1개만 저장
        verify(channelRepository, times(1)).save(any(Channel.class));
    }

    @Test
    @DisplayName("회원 생성 시 등록된 모든 이벤트 타입의 SettingEntry가 초기화된다")
    void initialize_createsSettingEntriesForAllEventTypes() {
        // given
        Long memberId = 1L;
        String email = "user@example.com";
        EventTypeDefinition def1 = mock(EventTypeDefinition.class);
        EventTypeDefinition def2 = mock(EventTypeDefinition.class);
        when(def1.getCode()).thenReturn("push.v1");
        when(def1.isEnabled()).thenReturn(true);
        when(def2.getCode()).thenReturn("chat.v1");
        when(def2.isEnabled()).thenReturn(true);
        when(eventTypeRegistry.getAllDefinitions()).thenReturn(List.of(def1, def2));
        when(channelRepository.save(any(Channel.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(settingEntryRepository.findByMemberIdAndEventTypeCode(eq(memberId), anyString())).thenReturn(Mono.empty());
        when(settingEntryRepository.save(any(SettingEntry.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        // when
        memberCreatedService.initialize(memberId, email).block();

        // then
        verify(settingEntryRepository, times(2)).save(any(SettingEntry.class));
    }

    @Test
    @DisplayName("이미 존재하는 채널은 중복 저장하지 않는다")
    void initialize_skipsDuplicateChannel() {
        // given
        Long memberId = 1L;
        String email = "user@example.com";
        when(eventTypeRegistry.getAllDefinitions()).thenReturn(List.of());
        when(channelRepository.save(any(Channel.class)))
                .thenReturn(Mono.error(new org.springframework.dao.DataIntegrityViolationException("duplicate")));

        // when & then — 예외가 전파되지 않고 완료된다
        memberCreatedService.initialize(memberId, email).block();
    }

    @Test
    @DisplayName("비활성화된 이벤트 타입의 SettingEntry는 생성하지 않는다")
    void initialize_skipsDisabledEventTypes() {
        // given
        Long memberId = 1L;
        String email = "user@example.com";
        EventTypeDefinition disabledDef = mock(EventTypeDefinition.class);
        when(disabledDef.isEnabled()).thenReturn(false);
        when(eventTypeRegistry.getAllDefinitions()).thenReturn(List.of(disabledDef));
        when(channelRepository.save(any(Channel.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        // when
        memberCreatedService.initialize(memberId, email).block();

        // then
        verify(settingEntryRepository, never()).save(any(SettingEntry.class));
    }

    @Test
    @DisplayName("SettingEntry 초기화 중 중복 오류가 발생해도 무시하고 완료된다")
    void initialize_skipsSettingEntryOnError() {
        // given
        Long memberId = 1L;
        EventTypeDefinition def = mock(EventTypeDefinition.class);
        when(def.getCode()).thenReturn("push.v1");
        when(def.isEnabled()).thenReturn(true);
        when(eventTypeRegistry.getAllDefinitions()).thenReturn(List.of(def));
        when(channelRepository.save(any(Channel.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(settingEntryRepository.findByMemberIdAndEventTypeCode(memberId, "push.v1")).thenReturn(Mono.empty());
        when(settingEntryRepository.save(any(SettingEntry.class)))
                .thenReturn(Mono.error(new org.springframework.dao.DataIntegrityViolationException("dup")));

        // when & then — onErrorResume 경로가 실행되어도 예외 전파 없이 완료
        memberCreatedService.initialize(memberId, "user@example.com").block();
    }
}
