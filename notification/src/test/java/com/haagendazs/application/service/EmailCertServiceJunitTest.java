package com.haagendazs.application.service;

import com.haagendazs.application.sender.NotificationSender;
import com.haagendazs.domain.model.ChannelType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailCertServiceJunitTest {

    private NotificationSender buildEmailSender() {
        NotificationSender sender = mock(NotificationSender.class);
        when(sender.channelType()).thenReturn(ChannelType.EMAIL);
        return sender;
    }

    @Test
    @DisplayName("이메일 인증 코드 발송이 정상적으로 완료된다")
    void sendCertificationEmail_success() {
        // given
        NotificationSender emailSender = buildEmailSender();
        EmailCertService service = new EmailCertService(List.of(emailSender));

        // when
        service.sendCertificationEmail("user@example.com", "123456").block();

        // then
        verify(emailSender, times(1)).send(eq("user@example.com"), anyString(), anyString());
    }

    @Test
    @DisplayName("발송 실패 시 최대 2회 재시도한다")
    void sendCertificationEmail_retries_onFailure() {
        // given
        NotificationSender emailSender = buildEmailSender();
        doThrow(new RuntimeException("SMTP 오류"))
                .doThrow(new RuntimeException("SMTP 오류"))
                .doNothing()
                .when(emailSender).send(anyString(), anyString(), anyString());
        EmailCertService service = new EmailCertService(List.of(emailSender));

        // when
        service.sendCertificationEmail("user@example.com", "123456").block();

        // then — 초기 시도 1 + 재시도 2 = 총 3회
        verify(emailSender, times(3)).send(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("재시도 횟수 초과 시 에러를 발생시킨다")
    void sendCertificationEmail_exhaustsRetries() {
        // given
        NotificationSender emailSender = buildEmailSender();
        doThrow(new RuntimeException("SMTP 영구 오류"))
                .when(emailSender).send(anyString(), anyString(), anyString());
        EmailCertService service = new EmailCertService(List.of(emailSender));

        // when & then
        org.junit.jupiter.api.Assertions.assertThrows(Exception.class,
                () -> service.sendCertificationEmail("user@example.com", "123456").block());
    }
}
