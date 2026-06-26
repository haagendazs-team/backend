package com.haagendazs.application.sender;

import com.haagendazs.domain.model.ChannelType;

public interface NotificationSender {
    ChannelType channelType();
    void send(String target, String subject, String body);
}
