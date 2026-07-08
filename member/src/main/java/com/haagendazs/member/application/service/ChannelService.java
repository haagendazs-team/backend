package com.haagendazs.member.application.service;

import com.haagendazs.common.exception.BusinessException;
import com.haagendazs.member.application.dto.ChannelMemberResult;
import com.haagendazs.member.application.dto.ChannelResult;
import com.haagendazs.member.domain.exception.MemberErrorCode;
import com.haagendazs.member.domain.model.Channel;
import com.haagendazs.member.domain.model.ChannelMember;
import com.haagendazs.member.domain.repository.ChannelMemberRepository;
import com.haagendazs.member.domain.repository.ChannelRepository;
import com.haagendazs.member.domain.repository.WorkspaceMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChannelService {

    private final ChannelRepository channelRepository;
    private final ChannelMemberRepository channelMemberRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final WorkspaceService workspaceService;

    public List<ChannelResult> getMyChannelsInWorkspace(Long memberId, Long workspaceId) {
        workspaceService.validateWorkspaceMember(memberId, workspaceId);

        List<Long> myChannelIds = channelMemberRepository.findAllByMemberId(memberId).stream()
                .map(ChannelMember::getChannelId)
                .toList();

        return channelRepository.findAllByWorkspaceIdAndChannelIdIn(workspaceId, myChannelIds).stream()
                .map(ChannelResult::from)
                .toList();
    }

    @Transactional
    public ChannelResult createChannel(Long memberId, Long workspaceId, String name) {
        workspaceService.validateWorkspaceMember(memberId, workspaceId);

        Channel channel = channelRepository.save(Channel.create(workspaceId, name, false));
        channelMemberRepository.save(ChannelMember.add(channel.getChannelId(), memberId));
        return ChannelResult.from(channel);
    }

    public ChannelResult getChannel(Long memberId, Long channelId) {
        Channel channel = getChannelOrThrow(channelId);
        validateChannelMember(memberId, channelId);
        workspaceService.validateWorkspaceMember(memberId, channel.getWorkspaceId());
        return ChannelResult.from(channel);
    }

    @Transactional
    public ChannelResult updateChannel(Long memberId, Long channelId, String name) {
        Channel channel = getChannelOrThrow(channelId);
        validateChannelMember(memberId, channelId);
        channel.updateName(name);
        return ChannelResult.from(channelRepository.save(channel));
    }

    @Transactional
    public void deleteChannel(Long memberId, Long channelId) {
        Channel channel = getChannelOrThrow(channelId);
        validateChannelMember(memberId, channelId);
        channelMemberRepository.deleteAllByChannelId(channelId);
        channelRepository.delete(channel);
    }

    public List<ChannelMemberResult> getChannelMembers(Long memberId, Long channelId) {
        Channel channel = getChannelOrThrow(channelId);
        validateChannelMember(memberId, channelId);
        workspaceService.validateWorkspaceMember(memberId, channel.getWorkspaceId());

        return channelMemberRepository.findAllByChannelId(channelId).stream()
                .map(ChannelMemberResult::from)
                .toList();
    }

    @Transactional
    public void leaveChannel(Long memberId, Long channelId) {
        validateChannelMember(memberId, channelId);
        channelMemberRepository.deleteByChannelIdAndMemberId(channelId, memberId);
    }

    @Transactional
    public ChannelResult getOrCreateDmChannel(Long memberId, Long workspaceId, Long targetMemberId) {
        if (memberId.equals(targetMemberId)) {
            throw new BusinessException(MemberErrorCode.CANNOT_INVITE_SELF);
        }

        workspaceService.validateWorkspaceMember(memberId, workspaceId);
        workspaceMemberRepository.findByWorkspaceIdAndMemberId(workspaceId, targetMemberId)
                .orElseThrow(() -> new BusinessException(MemberErrorCode.TARGET_MEMBER_NOT_IN_WORKSPACE));

        return channelRepository.findDmChannelByWorkspaceIdAndMemberIds(workspaceId, memberId, targetMemberId)
                .map(ChannelResult::from)
                .orElseGet(() -> createDmChannel(memberId, workspaceId, targetMemberId));
    }

    private ChannelResult createDmChannel(Long memberId, Long workspaceId, Long targetMemberId) {
        String dmName = "dm-" + Math.min(memberId, targetMemberId) + "-" + Math.max(memberId, targetMemberId);
        Channel channel = channelRepository.save(Channel.create(workspaceId, dmName, true));
        channelMemberRepository.save(ChannelMember.add(channel.getChannelId(), memberId));
        channelMemberRepository.save(ChannelMember.add(channel.getChannelId(), targetMemberId));
        return ChannelResult.from(channel);
    }

    private Channel getChannelOrThrow(Long channelId) {
        return channelRepository.findById(channelId)
                .orElseThrow(() -> new BusinessException(MemberErrorCode.CHANNEL_NOT_FOUND));
    }

    private void validateChannelMember(Long memberId, Long channelId) {
        if (!channelMemberRepository.existsByChannelIdAndMemberId(channelId, memberId)) {
            throw new BusinessException(MemberErrorCode.NOT_CHANNEL_MEMBER);
        }
    }
}
