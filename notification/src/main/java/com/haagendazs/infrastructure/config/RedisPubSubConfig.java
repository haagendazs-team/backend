package com.haagendazs.infrastructure.config;

import com.haagendazs.infrastructure.scheduler.ScheduledTriggerSubscriber;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.ReactiveRedisMessageListenerContainer;

@Configuration
public class RedisPubSubConfig {

    public static final String SCHEDULED_TRIGGER_CHANNEL = "notification:scheduled:trigger";
    public static final String STUCK_RECOVERY_CHANNEL = "notification:stuck:trigger";
    public static final String EVENT_TYPE_REGISTERED_CHANNEL = "notification:eventtype:registered";
    public static final String SSE_BROADCAST_CHANNEL = "notification:sse:send";

    @Bean
    public ChannelTopic scheduledTriggerTopic() {
        return new ChannelTopic(SCHEDULED_TRIGGER_CHANNEL);
    }

    @Bean
    public ChannelTopic stuckRecoveryTopic() {
        return new ChannelTopic(STUCK_RECOVERY_CHANNEL);
    }

    @Bean
    public ChannelTopic eventTypeRegisteredTopic() {
        return new ChannelTopic(EVENT_TYPE_REGISTERED_CHANNEL);
    }

    @Bean
    public ChannelTopic sseBroadcastTopic() {
        return new ChannelTopic(SSE_BROADCAST_CHANNEL);
    }

    @Bean
    public ReactiveRedisMessageListenerContainer reactiveRedisMessageListenerContainer(
            ReactiveRedisConnectionFactory connectionFactory,
            ScheduledTriggerSubscriber scheduledTriggerSubscriber,
            ChannelTopic eventTypeRegisteredTopic
    ) {
        ReactiveRedisMessageListenerContainer container = new ReactiveRedisMessageListenerContainer(connectionFactory);
        scheduledTriggerSubscriber.register(container);
        return container;
    }
}
