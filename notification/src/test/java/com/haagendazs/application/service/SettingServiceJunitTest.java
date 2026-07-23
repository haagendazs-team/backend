package com.haagendazs.application.service;

import com.haagendazs.application.dto.SettingResult;
import com.haagendazs.application.dto.UpdateSettingCommand;
import com.haagendazs.application.port.SettingCachePort;
import com.haagendazs.common.exception.BusinessException;
import com.haagendazs.domain.exception.NotificationErrorCode;
import com.haagendazs.domain.model.EventTypeDefinition;
import com.haagendazs.domain.model.SettingEntry;
import com.haagendazs.domain.repository.ChannelRepository;
import com.haagendazs.domain.repository.SettingEntryRepository;
import com.haagendazs.infrastructure.config.NotificationProperties;
import com.haagendazs.infrastructure.registry.EventTypeRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.anyString;

@ExtendWith(MockitoExtension.class)
class SettingServiceJunitTest {

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
    private SettingCachePort settingCachePort;

    private EventTypeDefinition enabledDef;

    @BeforeEach
    void setUp() {
        enabledDef = EventTypeDefinition.of("TICKET_OPEN", false, true, "티켓 오픈 알림", "티켓 판매가 시작될 때 알림을 받습니다.", "TICKET");
    }

    @Test
    @DisplayName("등록된 활성 이벤트 타입에 대해 설정 항목이 있으면 메타데이터와 함께 반환한다")
    void getSettings_existingEntry_returnsEntryResult() {
        // GIVEN
        SettingEntry entry = SettingEntry.create(1L, "TICKET_OPEN");
        when(eventTypeRegistry.getAllDefinitions()).thenReturn(List.of(enabledDef));
        when(settingEntryRepository.findByMemberIdAndEventTypeCode(1L, "TICKET_OPEN"))
                .thenReturn(Mono.just(entry));

        // WHEN
        List<SettingResult> results = settingService.getSettings(1L).collectList().block();

        // THEN
        assertThat(results).hasSize(1);
        assertThat(results.get(0).eventTypeCode()).isEqualTo("TICKET_OPEN");
        assertThat(results.get(0).enabled()).isTrue();
        assertThat(results.get(0).memberId()).isEqualTo(1L);
        assertThat(results.get(0).displayName()).isEqualTo("티켓 오픈 알림");
        assertThat(results.get(0).category()).isEqualTo("TICKET");
    }

    @Test
    @DisplayName("등록된 활성 이벤트 타입에 대해 설정 항목이 없으면 기본값 enabled=true와 메타데이터를 반환한다")
    void getSettings_noEntry_returnsDefaultEnabled() {
        // GIVEN
        when(eventTypeRegistry.getAllDefinitions()).thenReturn(List.of(enabledDef));
        when(settingEntryRepository.findByMemberIdAndEventTypeCode(1L, "TICKET_OPEN"))
                .thenReturn(Mono.empty());

        // WHEN
        List<SettingResult> results = settingService.getSettings(1L).collectList().block();

        // THEN
        assertThat(results).hasSize(1);
        assertThat(results.get(0).eventTypeCode()).isEqualTo("TICKET_OPEN");
        assertThat(results.get(0).enabled()).isTrue();
        assertThat(results.get(0).displayName()).isEqualTo("티켓 오픈 알림");
        assertThat(results.get(0).category()).isEqualTo("TICKET");
    }

    @Test
    @DisplayName("등록된 활성 이벤트 타입이 여러 개일 때 모든 설정 항목을 반환한다")
    void getSettings_multipleEventTypes_returnsAll() {
        // GIVEN
        EventTypeDefinition anotherDef = EventTypeDefinition.of("GAME_START", false, true, "경기 시작 알림", "경기 시작 30분 전에 알림을 받습니다.", "TICKET");
        SettingEntry entry1 = SettingEntry.create(1L, "TICKET_OPEN");
        SettingEntry entry2 = SettingEntry.create(1L, "GAME_START");
        when(eventTypeRegistry.getAllDefinitions()).thenReturn(List.of(enabledDef, anotherDef));
        when(settingEntryRepository.findByMemberIdAndEventTypeCode(1L, "TICKET_OPEN"))
                .thenReturn(Mono.just(entry1));
        when(settingEntryRepository.findByMemberIdAndEventTypeCode(1L, "GAME_START"))
                .thenReturn(Mono.just(entry2));

        // WHEN
        List<SettingResult> results = settingService.getSettings(1L).collectList().block();

        // THEN
        assertThat(results).hasSize(2);
        assertThat(results).extracting(SettingResult::eventTypeCode)
                .containsExactlyInAnyOrder("TICKET_OPEN", "GAME_START");
    }

    @Test
    @DisplayName("기존 설정 항목이 있을 때 updateSetting 호출 시 enabled 값을 업데이트하고 반환한다")
    void updateSetting_existingEntry_updatesAndReturns() {
        // GIVEN
        SettingEntry existing = SettingEntry.create(1L, "TICKET_OPEN");
        when(eventTypeRegistry.getByCode("TICKET_OPEN")).thenReturn(Optional.of(enabledDef));
        when(settingEntryRepository.findByMemberIdAndEventTypeCode(1L, "TICKET_OPEN"))
                .thenReturn(Mono.just(existing));
        when(settingEntryRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(settingCachePort.isCached(1L)).thenReturn(Mono.just(false));

        // WHEN
        SettingResult result = settingService.updateSetting(1L,
                new UpdateSettingCommand("TICKET_OPEN", false)).block();

        // THEN
        assertThat(result).isNotNull();
        assertThat(result.eventTypeCode()).isEqualTo("TICKET_OPEN");
        assertThat(result.enabled()).isFalse();
    }

    @Test
    @DisplayName("설정 항목이 없을 때 updateSetting 호출 시 새로 생성 후 반환한다")
    void updateSetting_noExistingEntry_createsAndReturns() {
        // GIVEN
        when(eventTypeRegistry.getByCode("TICKET_OPEN")).thenReturn(Optional.of(enabledDef));
        when(settingEntryRepository.findByMemberIdAndEventTypeCode(1L, "TICKET_OPEN"))
                .thenReturn(Mono.empty());
        when(settingEntryRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(settingCachePort.isCached(1L)).thenReturn(Mono.just(false));

        // WHEN
        SettingResult result = settingService.updateSetting(1L,
                new UpdateSettingCommand("TICKET_OPEN", true)).block();

        // THEN
        assertThat(result).isNotNull();
        assertThat(result.eventTypeCode()).isEqualTo("TICKET_OPEN");
        assertThat(result.enabled()).isTrue();
    }

    @Test
    @DisplayName("존재하지 않는 이벤트 타입 코드로 updateSetting 호출 시 EVENT_TYPE_NOT_FOUND 예외가 발생한다")
    void updateSetting_unknownEventTypeCode_throwsBusinessException() {
        // GIVEN
        when(eventTypeRegistry.getByCode("UNKNOWN")).thenReturn(Optional.empty());

        // WHEN
        Mono<SettingResult> result = settingService.updateSetting(1L,
                new UpdateSettingCommand("UNKNOWN", true));

        // THEN
        assertThatThrownBy(result::block)
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(NotificationErrorCode.EVENT_TYPE_NOT_FOUND));
    }

    @Test
    @DisplayName("캐시가 존재할 때 updateSetting 호출 시 캐시도 업데이트된다")
    void updateSetting_whenCached_updatesCacheEntry() {
        // GIVEN
        SettingEntry existing = SettingEntry.create(1L, "TICKET_OPEN");
        when(eventTypeRegistry.getByCode("TICKET_OPEN")).thenReturn(Optional.of(enabledDef));
        when(settingEntryRepository.findByMemberIdAndEventTypeCode(1L, "TICKET_OPEN"))
                .thenReturn(Mono.just(existing));
        when(settingEntryRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(settingCachePort.isCached(1L)).thenReturn(Mono.just(true));
        when(settingCachePort.put(eq(1L), anyString(), anyBoolean())).thenReturn(Mono.empty());

        // WHEN
        SettingResult result = settingService.updateSetting(1L,
                new UpdateSettingCommand("TICKET_OPEN", false)).block();

        // THEN
        assertThat(result).isNotNull();
        assertThat(result.enabled()).isFalse();
    }
}
