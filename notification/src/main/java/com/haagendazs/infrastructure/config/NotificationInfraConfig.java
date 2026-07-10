package com.haagendazs.infrastructure.config;

import io.netty.channel.ChannelOption;
import reactor.netty.http.server.HttpServer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.reactor.netty.NettyServerCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(NotificationProperties.class)
public class NotificationInfraConfig {

    @Bean
    public NettyServerCustomizer nettyServerCustomizer() {
        return server -> {
            HttpServer httpServer = (HttpServer) server;
            return httpServer
                    .option(ChannelOption.SO_BACKLOG, 65535)
                    .childOption(ChannelOption.SO_KEEPALIVE, true)
                    .childOption(ChannelOption.TCP_NODELAY, true)
                    .metrics(true, uri -> uri);
        };
    }
}
