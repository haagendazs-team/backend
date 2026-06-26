package com.haagendazs.application.service;

import com.haagendazs.application.port.NotificationStreamQueryPort;
import com.haagendazs.domain.model.EventTypeDefinition;
import com.haagendazs.infrastructure.publisher.ReactiveRedisStreamEventPublisher;
import com.haagendazs.infrastructure.registry.EventTypeRegistry;
import com.haagendazs.infrastructure.slack.SlackCommand;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SlackCommandService {

    private final NotificationStreamQueryPort streamQueryPort;
    private final ReactiveRedisStreamEventPublisher streamPublisher;
    private final EventTypeRegistry registry;

    public Mono<String> handle(String text) {
        Optional<SlackCommand> command = SlackCommand.resolve(text);
        return command.map(slackCommand -> switch (slackCommand) {
            case NOTIFY_EVENT -> handleNotifyEvent(text);
        }).orElseGet(() -> Mono.just("알 수 없는 명령어입니다.\n사용 가능한 명령어:\n" + buildUsageGuide()));
    }

    private Mono<String> handleNotifyEvent(String text) {
        Map<String, String> params = parseParams(text);
        String streamKey = params.get("streamKey");
        String id = params.get("id");

        if (streamKey == null || id == null) {
            return Mono.just("사용법: " + SlackCommand.NOTIFY_EVENT.getUsage());
        }

        Optional<EventTypeDefinition> defOpt = registry.getByStreamKey(streamKey);
        if (defOpt.isEmpty()) {
            return Mono.just("유효하지 않은 streamKey: " + streamKey);
        }
        EventTypeDefinition definition = defOpt.get();

        return streamQueryPort.findPayload(definition.getStreamKey(), id)
                .flatMap(payload -> streamPublisher.republish(definition.getStreamKey(), payload)
                        .thenReturn("재발송 완료 streamKey=" + streamKey + " id=" + id)
                        .doOnSuccess(r -> log.info("Slack 명령어로 알림 재발행 streamKey={} id={}", streamKey, id)))
                .defaultIfEmpty("해당 메시지를 찾을 수 없습니다. streamKey=" + streamKey + " id=" + id);
    }

    private Map<String, String> parseParams(String text) {
        Map<String, String> params = new HashMap<>();
        for (String token : text.split("\\s+")) {
            String[] kv = token.split("=", 2);
            if (kv.length == 2) {
                params.put(kv[0], kv[1]);
            }
        }
        return params;
    }

    private String buildUsageGuide() {
        StringBuilder sb = new StringBuilder();
        for (SlackCommand cmd : SlackCommand.values()) {
            sb.append("• /server ").append(cmd.getUsage()).append("\n");
        }
        return sb.toString();
    }
}
