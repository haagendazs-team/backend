package com.haagendazs.application.service;

import com.haagendazs.application.dto.event.MemberDeletedEvent;
import com.haagendazs.application.dto.event.MemberUpdatedEvent;
import com.haagendazs.application.dto.event.WorkspaceMemberJoinedEvent;
import com.haagendazs.domain.model.ChatMember;
import com.haagendazs.domain.repository.ChatMemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatMemberSyncService {
    private final ChatMemberRepository chatMemberRepository;

    @Transactional
    public void handleWorkspaceMemberJoined(WorkspaceMemberJoinedEvent workspaceMemberJoinEvent) {
        if(chatMemberRepository.existsById(workspaceMemberJoinEvent.memberId())){
            log.warn("이미 존재하는 chat_member입니다., memberId={}", workspaceMemberJoinEvent.memberId());
            return;
        }
        ChatMember chatMember = ChatMember.builder()
                .memberId(workspaceMemberJoinEvent.memberId())
                .nickname(workspaceMemberJoinEvent.nickname())
                .profileImageUrl(workspaceMemberJoinEvent.profileImageUrl())
                .build();
        chatMemberRepository.save(chatMember);
        log.info("chat_member 생성 완료, memberId={}", chatMember.getMemberId());
    }
    @Transactional
    public void handleMemberUpdated(MemberUpdatedEvent memberUpdateEvent) {
        //멤버 확인 후 Id가 존재하지 않는다면 새 객체를 생성한다.
        ChatMember chatmember = chatMemberRepository.findById(memberUpdateEvent.memberId()).orElseGet(()->
                ChatMember.builder()
                        .memberId(memberUpdateEvent.memberId())
                        .nickname(memberUpdateEvent.nickname())
                        .profileImageUrl(memberUpdateEvent.profileImageUrl())
                        .build());
        chatmember.updateProfile(memberUpdateEvent.nickname(), memberUpdateEvent.profileImageUrl());
        chatMemberRepository.save(chatmember);
        log.info("chat_member 갱신 완료,  memberId={}", memberUpdateEvent.memberId());
    }

    @Transactional
    public void handleMemberDeleted(MemberDeletedEvent memberDeleteEvent) {
        chatMemberRepository.deleteById(memberDeleteEvent.memberId());
        log.info("chat_member 삭제 완료, memberId={}", memberDeleteEvent.memberId());
    }

}
