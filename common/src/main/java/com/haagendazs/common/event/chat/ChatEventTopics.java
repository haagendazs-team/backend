package com.haagendazs.common.event.chat;

public final class ChatEventTopics {

    public static final String WORKSPACE_MEMBER_JOINED = "workspace-member.joined.v1";
    public static final String MEMBER_UPDATED = "member.updated.v1";
    public static final String MEMBER_DELETED = "member.deleted.v1";
    public static final String CHANNEL_CREATED = "member.chat.channel_created.v1";
    public static final String CHANNEL_UPDATED = "member.chat.channel_updated.v1";
    public static final String CHANNEL_DELETED = "member.chat.channel_deleted.v1";
    public static final String CHANNEL_MEMBER_JOINED = "member.chat.channel_member_joined.v1";
    public static final String CHANNEL_MEMBER_DELETED = "member.chat.channel_member_deleted.v1";

    private ChatEventTopics() {
    }
}
