package com.haagendazs.presentation.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * OUTER API — dev 프로파일 전용 알림 이벤트 발행 엔드포인트.
 */
@Slf4j
@RestController
@RequestMapping("/dev/events")
@Profile("local")
@RequiredArgsConstructor
public class DevEventController {

    private final ReactiveStringRedisTemplate redisTemplate;

    @PostMapping("/publish")
    public Mono<ResponseEntity<Map<String, String>>> publish(@RequestBody PublishRequest request) {
        return redisTemplate.opsForStream()
                .add(request.stream(), Map.of("payload", request.payload()))
                .doOnSuccess(v -> log.info("DEV event published stream={}", request.stream()))
                .thenReturn(ResponseEntity.ok(Map.of("stream", request.stream(), "status", "published")));
    }

    record PublishRequest(String stream, String payload) {}
}
