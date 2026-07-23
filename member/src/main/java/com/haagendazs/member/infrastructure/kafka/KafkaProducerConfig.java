package com.haagendazs.member.infrastructure.kafka;

import com.haagendazs.common.event.chat.ChatEventTopics;
import com.haagendazs.common.event.search.SearchEventTopics;
import com.haagendazs.common.event.payment.PaymentEventTopics;
import com.haagendazs.common.event.notification.NotificationEventTopics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaProducerConfig {

    @Bean
    NewTopic memberCreatedTopic() {
        return TopicBuilder.name(SearchEventTopics.CREATED)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    NewTopic memberUpdatedTopic() {
        return TopicBuilder.name(SearchEventTopics.UPDATED)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    NewTopic memberDeactivatedTopic() {
        return TopicBuilder.name(SearchEventTopics.DEACTIVATED)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    NewTopic memberWorkspaceJoinedTopic() {
        return TopicBuilder.name(SearchEventTopics.WORKSPACE_JOINED)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    NewTopic memberWorkspaceLeftTopic() {
        return TopicBuilder.name(SearchEventTopics.WORKSPACE_LEFT)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    NewTopic memberChannelJoinedTopic() {
        return TopicBuilder.name(SearchEventTopics.CHANNEL_JOINED)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    NewTopic memberChannelLeftTopic() {
        return TopicBuilder.name(SearchEventTopics.CHANNEL_LEFT)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    NewTopic memberChannelCreatedTopic() {
        return TopicBuilder.name(SearchEventTopics.CHANNEL_CREATED)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    NewTopic memberChannelRenamedTopic() {
        return TopicBuilder.name(SearchEventTopics.CHANNEL_RENAMED)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    NewTopic memberChannelDeletedTopic() {
        return TopicBuilder.name(SearchEventTopics.CHANNEL_DELETED)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    NewTopic memberWorkspaceDeletedTopic() {
        return TopicBuilder.name(SearchEventTopics.WORKSPACE_DELETED)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    NewTopic memberEmailCertTopic() {
        return TopicBuilder.name(NotificationEventTopics.MEMBER_EMAIL_CERT)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    NewTopic memberPushTopic() {
        return TopicBuilder.name(NotificationEventTopics.MEMBER_PUSH)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    NewTopic chatWorkspaceMemberJoinedTopic() {
        return TopicBuilder.name(ChatEventTopics.WORKSPACE_MEMBER_JOINED)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    NewTopic chatMemberDeletedTopic() {
        return TopicBuilder.name(ChatEventTopics.MEMBER_DELETED)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    NewTopic chatChannelCreatedTopic() {
        return TopicBuilder.name(ChatEventTopics.CHANNEL_CREATED)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    NewTopic chatChannelUpdatedTopic() {
        return TopicBuilder.name(ChatEventTopics.CHANNEL_UPDATED)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    NewTopic chatChannelDeletedTopic() {
        return TopicBuilder.name(ChatEventTopics.CHANNEL_DELETED)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    NewTopic chatChannelMemberJoinedTopic() {
        return TopicBuilder.name(ChatEventTopics.CHANNEL_MEMBER_JOINED)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    NewTopic chatChannelMemberDeletedTopic() {
        return TopicBuilder.name(ChatEventTopics.CHANNEL_MEMBER_DELETED)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    NewTopic paymentWorkspaceCreatedTopic() {
        return TopicBuilder.name(PaymentEventTopics.WORKSPACE_CREATED)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
