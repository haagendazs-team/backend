package com.haagendazs.presentation.stomp;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker//특정 토픽에 메시지를 전달하는 브로커 역할
@RequiredArgsConstructor
public class StompWebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final StompHandler stompHandler;


    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/connect")
                .setAllowedOrigins("http://localhost:3001")//front end 환경인 3001번에 맞춰줌
//            ws://가 아닌 http:// 엔드포인트를 사용할 수 있게 해주는 sockJs라이브러리를 통한 요청을 허용하는 설정
                .withSockJS();
        // k6 성능 테스트를 위한 순수 웹소켓 추후에는 보안 이슈가 발생하니 특정 도메인만 작성하여 접근 제한을 꼭 해주어야함.
        registry.addEndpoint("/ws")
                .setAllowedOrigins("*");
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
//        /publish/1 ->이러한 형태(룰)을 가지고 메시지를 발행
//        /publish로 시작하는 url패턴으로 메시지가 발행되면 @Controller 객체의 @MessageMapping메서드로 라우팅
        registry.setApplicationDestinationPrefixes("/publish");
//        /topic/1형태로 메시지를 수신(subscribe)해야 함을 설정
        registry.enableSimpleBroker("/topic");
    }

    //    웹소켓요청(connect, subscribe, disconnect)등의 요청시에는 http header등 http메시지를 넣어 올 수 있고,
//    이를 interceptor을 통해 가로채 토큰 등을 검증할 수 있음
    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(stompHandler);
    }

}

