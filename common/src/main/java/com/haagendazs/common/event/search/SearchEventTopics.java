package com.haagendazs.common.event.search;

public final class SearchEventTopics {

    public static final String CREATED = "member.created.v1";
    public static final String UPDATED = "member.updated.v1";
    public static final String DEACTIVATED = "member.deactivated.v1";
    public static final String WORKSPACE_JOINED = "member.workspace-joined.v1";
    public static final String WORKSPACE_LEFT = "member.workspace-left.v1";
    public static final String CHANNEL_JOINED = "member.channel-joined.v1";
    public static final String CHANNEL_LEFT = "member.channel-left.v1";
    public static final String CHANNEL_CREATED = "member.channel-created.v1";
    public static final String CHANNEL_RENAMED = "member.channel-renamed.v1";
    public static final String CHANNEL_DELETED = "member.channel-deleted.v1";
    public static final String WORKSPACE_DELETED = "member.workspace-deleted.v1";

    private SearchEventTopics() {
    }
}
