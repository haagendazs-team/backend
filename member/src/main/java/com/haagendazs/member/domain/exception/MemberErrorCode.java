package com.haagendazs.member.domain.exception;

import com.haagendazs.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum MemberErrorCode implements ErrorCode {

    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "M001", "이미 사용 중인 이메일입니다."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "M002", "이메일 또는 비밀번호가 올바르지 않습니다."),
    MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "M003", "회원을 찾을 수 없습니다."),
    INACTIVE_MEMBER(HttpStatus.FORBIDDEN, "M004", "비활성화된 계정입니다."),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "M005", "유효하지 않은 리프레시 토큰입니다."),
    EXPIRED_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "M006", "만료된 리프레시 토큰입니다."),
    WORKSPACE_NOT_FOUND(HttpStatus.NOT_FOUND, "M007", "워크스페이스를 찾을 수 없습니다."),
    NOT_WORKSPACE_MEMBER(HttpStatus.FORBIDDEN, "M008", "워크스페이스 멤버가 아닙니다."),
    ALREADY_WORKSPACE_MEMBER(HttpStatus.CONFLICT, "M009", "이미 워크스페이스에 속한 멤버입니다."),
    INSUFFICIENT_PERMISSION(HttpStatus.FORBIDDEN, "M010", "권한이 없습니다."),
    CHANNEL_NOT_FOUND(HttpStatus.NOT_FOUND, "M011", "채널을 찾을 수 없습니다."),
    NOT_CHANNEL_MEMBER(HttpStatus.FORBIDDEN, "M012", "채널 멤버가 아닙니다."),
    ALREADY_CHANNEL_MEMBER(HttpStatus.CONFLICT, "M013", "이미 채널에 참여 중입니다."),
    CANNOT_INVITE_SELF(HttpStatus.BAD_REQUEST, "M014", "자기 자신을 초대할 수 없습니다."),
    TARGET_MEMBER_NOT_IN_WORKSPACE(HttpStatus.BAD_REQUEST, "M015", "대상 멤버가 워크스페이스에 속해 있지 않습니다."),
    INVALID_INVITE_ROLE(HttpStatus.BAD_REQUEST, "M016", "초대 역할은 MEMBER 또는 ADMIN만 가능합니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
