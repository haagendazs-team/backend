package com.haagendazs.member.application.service;

import com.haagendazs.common.exception.BusinessException;
import com.haagendazs.member.application.dto.MemberResult;
import com.haagendazs.member.application.port.ChatEventPublisher;
import com.haagendazs.member.application.port.MemberEventPublisher;
import com.haagendazs.member.domain.exception.MemberErrorCode;
import com.haagendazs.member.domain.model.Member;
import com.haagendazs.member.domain.repository.MemberRepository;
import com.haagendazs.member.domain.repository.TokenRepository;
import com.haagendazs.member.fixture.TestFixture;
import com.haagendazs.member.infrastructure.kafka.TransactionAfterCommitExecutor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemberServiceJunitTest {

    private static final String EMAIL = "example@example.com";
    private static final String NICKNAME = "user";

    @InjectMocks
    private MemberService memberService;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private TokenRepository tokenRepository;

    @Mock
    private MemberEventPublisher memberEventPublisher;

    @Mock
    private ChatEventPublisher chatEventPublisher;

    @Mock
    private TransactionAfterCommitExecutor afterCommitExecutor;

    @Test
    @DisplayName("[Happy] 내 프로필 조회에 성공하면 활성 회원 정보를 반환한다")
    void getMyProfile_success_returnsActiveMember() {
        Member member = TestFixture.member(1L, EMAIL, "encoded", NICKNAME);
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));

        MemberResult result = memberService.getMyProfile(1L);

        assertThat(result.memberId()).isEqualTo(1L);
        assertThat(result.nickname()).isEqualTo(NICKNAME);
    }

    @Test
    @DisplayName("[Happy] 다른 회원 프로필 조회에 성공하면 활성 회원 정보를 반환한다")
    void getMemberProfile_success_returnsActiveMember() {
        Member member = TestFixture.member(2L, "example2@example.com", "encoded", "user2");
        when(memberRepository.findById(2L)).thenReturn(Optional.of(member));

        MemberResult result = memberService.getMemberProfile(2L);

        assertThat(result.memberId()).isEqualTo(2L);
        assertThat(result.nickname()).isEqualTo("user2");
    }

    @Test
    @DisplayName("[Exception] 존재하지 않는 회원 프로필 조회 시 MEMBER_NOT_FOUND 예외가 발생한다")
    void getMemberProfile_notFound_throwsException() {
        when(memberRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> memberService.getMemberProfile(99L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(MemberErrorCode.MEMBER_NOT_FOUND));
    }

    @Test
    @DisplayName("[Exception] 비활성 회원 프로필 조회 시 INACTIVE_MEMBER 예외가 발생한다")
    void getMyProfile_inactiveMember_throwsException() {
        Member member = TestFixture.inactiveMember(1L, EMAIL, "encoded", NICKNAME);
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));

        assertThatThrownBy(() -> memberService.getMyProfile(1L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(MemberErrorCode.INACTIVE_MEMBER));
    }

    @Test
    @DisplayName("[Exception] 비활성 회원 프로필 조회 시 INACTIVE_MEMBER 예외가 발생한다")
    void getMemberProfile_inactiveMember_throwsException() {
        Member member = TestFixture.inactiveMember(2L, "example2@example.com", "encoded", "user2");
        when(memberRepository.findById(2L)).thenReturn(Optional.of(member));

        assertThatThrownBy(() -> memberService.getMemberProfile(2L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(MemberErrorCode.INACTIVE_MEMBER));
    }

    @Test
    @DisplayName("[Happy] 프로필 수정에 성공하면 변경된 닉네임과 이미지 URL을 반환한다")
    void updateMyProfile_success_returnsUpdatedProfile() {
        Member member = TestFixture.member(1L, EMAIL, "encoded", NICKNAME);
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));
        doAnswer(invocation -> {
            Runnable action = invocation.getArgument(0);
            action.run();
            return null;
        }).when(afterCommitExecutor).runAfterCommit(any(Runnable.class));
        when(memberRepository.save(any(Member.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MemberResult result = memberService.updateMyProfile(1L, "user2", "https://image.example.com/profile.png");

        assertThat(result.nickname()).isEqualTo("user2");
        assertThat(result.profileImageUrl()).isEqualTo("https://image.example.com/profile.png");
        verify(chatEventPublisher).publishMemberUpdated(member);
    }

    @Test
    @DisplayName("[Happy] 회원 탈퇴 시 계정을 비활성화하고 Refresh Token을 삭제한다")
    void withdraw_success_deactivatesMemberAndDeletesToken() {
        Member member = TestFixture.member(1L, EMAIL, "encoded", NICKNAME);
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));
        doAnswer(invocation -> {
            Runnable action = invocation.getArgument(0);
            action.run();
            return null;
        }).when(afterCommitExecutor).runAfterCommit(any(Runnable.class));
        when(memberRepository.save(any(Member.class))).thenAnswer(invocation -> invocation.getArgument(0));

        memberService.withdraw(1L);

        assertThat(member.getIsActive()).isFalse();
        verify(tokenRepository).deleteByMemberId(1L);
        verify(memberEventPublisher).publishDeactivated(member);
        verify(chatEventPublisher).publishMemberDeleted(member);
    }
}
