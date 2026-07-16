package com.haagendazs.member.application.port;

import com.haagendazs.common.event.notification.PushNotificationPayload;

public interface NotificationEventPublisher {

    void publishEmailCert(String email, String code);

    void publishPush(PushNotificationPayload payload);
}
