package com.haagendazs.infrastructure.sender;

import com.haagendazs.application.sender.NotificationSender;
import com.haagendazs.domain.model.ChannelType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class EmailNotificationSender implements NotificationSender {

    private final JavaMailSender mailSender;

    @Override
    public ChannelType channelType() {
        return ChannelType.EMAIL;
    }

    // 블로킹 JavaMailSender — Dispatcher에서 Schedulers.boundedElastic()로 래핑하여 호출됨
    @Override
    public void send(String target, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(target);
        message.setSubject(subject);
        message.setText(body);
        mailSender.send(message);
        log.debug("EMAIL sent to {}", target);
    }
}
