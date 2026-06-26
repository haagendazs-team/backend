package com.haagendazs.infrastructure.startup;

import com.haagendazs.domain.repository.EventTypeRepository;
import com.haagendazs.infrastructure.consumer.StreamSubscriptionManager;
import com.haagendazs.infrastructure.registry.EventTypeRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class EventTypeStartupLoader implements ApplicationRunner {

    private final EventTypeRepository eventTypeRepository;
    private final EventTypeRegistry registry;
    private final StreamSubscriptionManager subscriptionManager;

    @Override
    public void run(ApplicationArguments args) {
        eventTypeRepository.findAllEnabled()
                .doOnNext(def -> {
                    registry.register(def);
                    subscriptionManager.startSubscription(def);
                    log.info("이벤트 타입 로드 완료 code={} streamKey={}", def.getCode(), def.getStreamKey());
                })
                .blockLast();
        log.info("EventType 시작 로드 완료 총 {}개", registry.getAllDefinitions().size());
    }
}
