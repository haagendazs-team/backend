package com.haagendazs.application.service;

import com.haagendazs.application.sender.NotificationSender;
import com.haagendazs.domain.model.ChannelType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
public class EmailCertService {

    private static final int MAX_RETRY = 2;
    private static final Duration RETRY_DELAY = Duration.ofSeconds(3);

    private final NotificationSender emailSender;

    public EmailCertService(List<NotificationSender> senders) {
        Map<ChannelType, NotificationSender> senderMap = senders.stream()
                .collect(Collectors.toMap(NotificationSender::channelType, Function.identity()));
        this.emailSender = senderMap.get(ChannelType.EMAIL);
    }

    public Mono<Void> sendCertificationEmail(String email, String code) {
        String subject = "[Haagendazs] 이메일 인증 코드";
        String body = "인증 코드: " + code;

        return Mono.fromRunnable(() -> emailSender.send(email, subject, body))
                .subscribeOn(Schedulers.boundedElastic())
                .retryWhen(Retry.backoff(MAX_RETRY, RETRY_DELAY)
                        .doBeforeRetry(signal -> log.warn("이메일 인증 발송 재시도 attempt={} email={}",
                                signal.totalRetries() + 1, email)))
                .doOnSuccess(v -> log.info("이메일 인증 발송 완료 email={}", email))
                .doOnError(e -> log.error("이메일 인증 발송 최종 실패 email={}", email, e))
                .then();
    }
}
