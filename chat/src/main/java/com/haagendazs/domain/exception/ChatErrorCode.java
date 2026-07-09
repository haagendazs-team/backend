package com.haagendazs.domain.exception;

import com.haagendazs.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ChatErrorCode implements ErrorCode {

    CHAT_NOT_FOUND(HttpStatus.NOT_FOUND, "C001", "채팅방을 찾을 수 없습니다."),
    INVALID_STOMP_TOKEN_HEADER(HttpStatus.UNAUTHORIZED, "C002", "유효하지 않은 STOMP 토큰 헤더입니다."),
    NOT_A_ROOM_MEMBER(HttpStatus.FORBIDDEN, "C003", "해당 채널의 참여자가 아닙니다."),
    MESSAGE_NOT_FOUND(HttpStatus.NOT_FOUND, "C004", "메시지를 찾을 수 없습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
