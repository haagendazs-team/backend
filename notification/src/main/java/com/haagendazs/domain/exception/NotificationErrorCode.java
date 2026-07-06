package com.haagendazs.domain.exception;

import com.haagendazs.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum NotificationErrorCode implements ErrorCode {

    NOTIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "N001", "알림을 찾을 수 없습니다."),
    NOTIFICATION_ALREADY_READ(HttpStatus.BAD_REQUEST, "N002", "이미 읽은 알림입니다."),
    NOTIFICATION_SETTING_NOT_FOUND(HttpStatus.NOT_FOUND, "N003", "알림 설정을 찾을 수 없습니다."),
    NOTIFICATION_CHANNEL_NOT_FOUND(HttpStatus.NOT_FOUND, "N004", "알림 채널을 찾을 수 없습니다."),
    NOTIFICATION_CHANNEL_ALREADY_EXISTS(HttpStatus.CONFLICT, "N005", "이미 등록된 알림 채널입니다."),
    NOTIFICATION_CHANNEL_LIMIT_EXCEEDED(HttpStatus.BAD_REQUEST, "N006", "알림 채널은 최대 2개까지 등록 가능합니다."),
    NOTIFICATION_CHANNEL_TYPE_UNSUPPORTED(HttpStatus.BAD_REQUEST, "N007", "지원하지 않는 알림 채널 타입입니다."),
    NOTIFICATION_SEND_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "N008", "알림 발송에 실패했습니다."),
    EVENT_TYPE_NOT_FOUND(HttpStatus.NOT_FOUND, "N009", "존재하지 않는 이벤트 타입입니다."),
    EVENT_TYPE_DUPLICATE(HttpStatus.CONFLICT, "N010", "이미 등록된 이벤트 타입입니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
