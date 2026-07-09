package com.haagendazs.presentation.stomp;

import com.haagendazs.common.exception.BusinessException;
import com.haagendazs.domain.exception.ChatErrorCode;
import com.haagendazs.domain.repository.ChatParticipantRepository;
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

    private final ChatParticipantRepository chatParticipantRepository;
    // TODO: JWT 검증 유틸(JwtTokenUtil, TokenPayload)이 common에 추가되면 주입받아 교체

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        final StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);

        if (StompCommand.CONNECT == accessor.getCommand()) {
            log.info("CONNECT 요청 시 토큰 유효성 검증");
            String token = getValidToken(accessor);

            // TODO: JWT 유틸 확정되면 아래로 교체
            // TokenPayload payload = jwtTokenUtil.parseToken(token);
            // Long memberId = payload.accountId();

            // 임시: 헤더에서 memberId 직접 받아서 테스트 (JWT 유틸 나오기 전까지)
            Long memberId = extractTempMemberId(accessor);

            Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
            if (sessionAttributes != null) {
                sessionAttributes.put("memberId", memberId);
            }
            log.info("토큰 검증 완료, memberId={}", memberId);
        }

        if (StompCommand.SUBSCRIBE == accessor.getCommand()) {
            log.info("SUBSCRIBE 검증");
            Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
            if (sessionAttributes == null || !sessionAttributes.containsKey("memberId")) {
                throw new BusinessException(ChatErrorCode.INVALID_STOMP_TOKEN_HEADER);
            }

            Long memberId = (Long) sessionAttributes.get("memberId");
            String channelId = accessor.getDestination().split("/")[2];

            if (!chatParticipantRepository.existsByChannelIdAndMemberId(Long.parseLong(channelId), memberId)) {
                log.info("NOT_A_ROOM_MEMBER 발생");
                throw new BusinessException(ChatErrorCode.NOT_A_ROOM_MEMBER);
            }
        }
        return message;
    }

    private String getValidToken(StompHeaderAccessor accessor) {
        String bearerToken = accessor.getFirstNativeHeader("Authorization");
        if (bearerToken == null || !bearerToken.startsWith("Bearer ")) {
            throw new BusinessException(ChatErrorCode.INVALID_STOMP_TOKEN_HEADER);
        }
        return bearerToken.substring(7);
    }

    // TODO: JWT 유틸 완성되면 이 메서드 삭제
    private Long extractTempMemberId(StompHeaderAccessor accessor) {
        String memberIdHeader = accessor.getFirstNativeHeader("memberId");
        if (memberIdHeader == null) {
            throw new BusinessException(ChatErrorCode.INVALID_STOMP_TOKEN_HEADER);
        }
        return Long.parseLong(memberIdHeader);
    }
}
