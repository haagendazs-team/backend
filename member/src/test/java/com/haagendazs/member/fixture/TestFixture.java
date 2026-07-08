package com.haagendazs.member.fixture;

import com.haagendazs.member.domain.model.Channel;
import com.haagendazs.member.domain.model.Member;
import com.haagendazs.member.domain.model.Token;
import com.haagendazs.member.domain.model.Workspace;
import com.haagendazs.member.domain.model.WorkspaceMember;
import com.haagendazs.member.domain.model.WorkspaceRole;

import java.lang.reflect.Field;
import java.time.LocalDateTime;

public final class TestFixture {

    private TestFixture() {
    }

    public static Member member(Long memberId, String email, String encodedPassword, String nickname) {
        Member member = Member.create(email, encodedPassword, nickname);
        setField(member, "memberId", memberId);
        setField(member, "createdAt", LocalDateTime.now());
        return member;
    }

    public static Member inactiveMember(Long memberId, String email, String encodedPassword, String nickname) {
        Member member = member(memberId, email, encodedPassword, nickname);
        member.deactivate();
        return member;
    }

    public static Token token(Long id, Long memberId, String refreshToken, LocalDateTime expiredAt) {
        Token token = Token.create(memberId, refreshToken, expiredAt);
        setField(token, "id", id);
        setField(token, "createdAt", LocalDateTime.now());
        return token;
    }

    public static Workspace workspace(Long workspaceId, String name) {
        Workspace workspace = Workspace.create(name, null, "FREE");
        setField(workspace, "workspaceId", workspaceId);
        setField(workspace, "createdAt", LocalDateTime.now());
        return workspace;
    }

    public static WorkspaceMember workspaceMember(Long workspaceId, Long memberId, WorkspaceRole role) {
        WorkspaceMember workspaceMember = WorkspaceMember.assign(workspaceId, memberId, role);
        setField(workspaceMember, "id", 1L);
        setField(workspaceMember, "createdAt", LocalDateTime.now());
        return workspaceMember;
    }

    public static Channel channel(Long channelId, Long workspaceId, String name, boolean isDirectMessage) {
        Channel channel = Channel.create(workspaceId, name, isDirectMessage);
        setField(channel, "channelId", channelId);
        setField(channel, "createdAt", LocalDateTime.now());
        return channel;
    }

    private static void setField(Object target, String fieldName, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("테스트 픽스처 필드 설정 실패: " + fieldName, e);
        }
    }
}
