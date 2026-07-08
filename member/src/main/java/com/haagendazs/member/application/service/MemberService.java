package com.haagendazs.member.application.service;

import com.haagendazs.common.exception.BusinessException;
import com.haagendazs.member.application.dto.MemberResult;
import com.haagendazs.member.domain.exception.MemberErrorCode;
import com.haagendazs.member.domain.model.Member;
import com.haagendazs.member.domain.repository.MemberRepository;
import com.haagendazs.member.domain.repository.TokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberService {

    private final MemberRepository memberRepository;
    private final TokenRepository tokenRepository;

    public MemberResult getMyProfile(Long memberId) {
        return MemberResult.from(getActiveMember(memberId));
    }

    public MemberResult getMemberProfile(Long memberId) {
        return MemberResult.from(getActiveMember(memberId));
    }

    @Transactional
    public MemberResult updateMyProfile(Long memberId, String nickname, String profileImageUrl) {
        Member member = getActiveMember(memberId);
        member.updateProfile(nickname, profileImageUrl);
        return MemberResult.from(memberRepository.save(member));
    }

    @Transactional
    public void withdraw(Long memberId) {
        Member member = getActiveMember(memberId);
        member.deactivate();
        memberRepository.save(member);
        tokenRepository.deleteByMemberId(memberId);
    }

    private Member getActiveMember(Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(MemberErrorCode.MEMBER_NOT_FOUND));

        if (!Boolean.TRUE.equals(member.getIsActive())) {
            throw new BusinessException(MemberErrorCode.INACTIVE_MEMBER);
        }

        return member;
    }
}
