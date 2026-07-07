package com.haagendazs.presentation.stomp;

import com.haagendazs.domain.exception.ChatErrorCode;
import com.haagendazs.application.service.ChatService;
import com.haagendazs.global.exception.AppException;
import com.haagendazs.global.security.jwt.JwtTokenUtil;
import com.haagendazs.global.security.jwt.dto.TokenPayload;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class StompHandler implements ChannelInterceptor {

    private final JwtTokenUtil jwtTokenUtil;
    private final ChatService chatService;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        final StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);

        if (StompCommand.CONNECT == accessor.getCommand()) {
            log.info("CONNECT 요청 시 토큰 유효성 검증");
            String token = getValidToken(accessor);
            TokenPayload payload = jwtTokenUtil.parseToken(token);
            log.info("토큰 검증 완료");
            Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
            if (sessionAttributes != null) {
                Long memberId = payload.accountId();
                sessionAttributes.put("memberId", memberId);
            }
        }

        if (StompCommand.SUBSCRIBE == accessor.getCommand()) {
            log.info("SUBSCRIBE 검증");
            Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
            if (sessionAttributes == null || !sessionAttributes.containsKey("memberId")) {
                throw new AppException(ChatErrorCode.INVALID_STOMP_TOKEN_HEADER);
            }

            Long memberId = (Long) sessionAttributes.get("memberId");
            String channelId = accessor.getDestination().split("/")[2];

            if (!chatService.isRoomParticipant(memberId, Long.parseLong(channelId))) {
                log.info("NOT_A_ROOM_MEMBER 발생");
                throw new AppException(ChatErrorCode.NOT_A_ROOM_MEMBER);
            }
        }
        return message;
    }

    private String getValidToken(StompHeaderAccessor accessor) {
        String bearerToken = accessor.getFirstNativeHeader("Authorization");
        if (bearerToken == null || !bearerToken.startsWith("Bearer ")) {
            throw new AppException(ChatErrorCode.INVALID_STOMP_TOKEN_HEADER);
        }
        return bearerToken.substring(7);
    }
}
