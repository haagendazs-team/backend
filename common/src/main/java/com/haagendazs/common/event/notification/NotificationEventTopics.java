package com.haagendazs.common.event.notification;

public final class NotificationEventTopics {

    public static final String MEMBER_EMAIL_CERT = "member.notif.email-cert.v1";
    public static final String MEMBER_PUSH = "member.notif.push.v1";

    private NotificationEventTopics() {
    }

    public static String domainPush(String domain) {
        return domain + ".notif.push.v1";
    }
}
