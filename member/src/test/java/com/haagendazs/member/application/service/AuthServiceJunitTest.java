package com.haagendazs.member.application.service;

import com.haagendazs.common.exception.BusinessException;
import com.haagendazs.member.application.dto.MemberResult;
import com.haagendazs.member.application.dto.TokenResult;
import com.haagendazs.member.application.port.MemberEventPublisher;
import com.haagendazs.member.application.port.NotificationEventPublisher;
import com.haagendazs.member.domain.exception.MemberErrorCode;
import com.haagendazs.member.domain.model.Member;
import com.haagendazs.member.domain.model.Token;
import com.haagendazs.member.domain.repository.MemberRepository;
import com.haagendazs.member.domain.repository.TokenRepository;
import com.haagendazs.member.fixture.TestFixture;
import com.haagendazs.member.infrastructure.config.JwtProperties;
import com.haagendazs.member.infrastructure.kafka.EmailVerificationCodeGenerator;
import com.haagendazs.member.infrastructure.kafka.TransactionAfterCommitExecutor;
import com.haagendazs.member.infrastructure.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceJunitTest {

    private static final String EMAIL = "example@example.com";
    private static final String UNKNOWN_EMAIL = "unknown@example.com";
    private static final String NICKNAME = "user";
    private static final String PASSWORD = "password";

    @InjectMocks
    private AuthService authService;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private TokenRepository tokenRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private JwtProperties jwtProperties;

    @Mock
    private MemberEventPublisher memberEventPublisher;

    @Mock
    private NotificationEventPublisher notificationEventPublisher;

    @Mock
    private EmailVerificationCodeGenerator emailVerificationCodeGenerator;

    @Mock
    private TransactionAfterCommitExecutor afterCommitExecutor;

    @BeforeEach
    void setUp() {
        lenient().when(jwtProperties.getRefreshExpirationMs()).thenReturn(3_600_000L);
    }

    @Test
    @DisplayName("[Exception] 회원가입 시 이메일이 중복되면 EMAIL_ALREADY_EXISTS 예외가 발생한다")
    void signup_duplicateEmail_throwsException() {
        when(memberRepository.existsByEmail(EMAIL)).thenReturn(true);

        assertThatThrownBy(() -> authService.signup(EMAIL, PASSWORD, NICKNAME))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(MemberErrorCode.EMAIL_ALREADY_EXISTS));
    }

    @Test
    @DisplayName("[Happy] 회원가입에 성공하면 저장된 회원 정보를 반환한다")
    void signup_success_returnsMemberResult() {
        when(memberRepository.existsByEmail(EMAIL)).thenReturn(false);
        when(passwordEncoder.encode(PASSWORD)).thenReturn("encoded-password");
        when(emailVerificationCodeGenerator.generate()).thenReturn("123456");
        doAnswer(invocation -> {
            Runnable action = invocation.getArgument(0);
            action.run();
            return null;
        }).when(afterCommitExecutor).runAfterCommit(any(Runnable.class));
        when(memberRepository.save(any(Member.class))).thenAnswer(invocation -> {
            Member member = invocation.getArgument(0);
            return TestFixture.member(1L, member.getEmail(), member.getPassword(), member.getNickname());
        });

        MemberResult result = authService.signup(EMAIL, PASSWORD, NICKNAME);

        assertThat(result.memberId()).isEqualTo(1L);
        assertThat(result.email()).isEqualTo(EMAIL);
        assertThat(result.nickname()).isEqualTo(NICKNAME);
        verify(notificationEventPublisher).publishEmailCert(EMAIL, "123456");
        verify(memberEventPublisher).publishCreated(any(Member.class));
    }

    @Test
    @DisplayName("[Exception] 존재하지 않는 이메일로 로그인하면 INVALID_CREDENTIALS 예외가 발생한다")
    void login_unknownEmail_throwsException() {
        when(memberRepository.findByEmail(UNKNOWN_EMAIL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(UNKNOWN_EMAIL, PASSWORD))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(MemberErrorCode.INVALID_CREDENTIALS));
    }

    @Test
    @DisplayName("[Exception] 비활성 회원은 로그인 시 INACTIVE_MEMBER 예외가 발생한다")
    void login_inactiveMember_throwsException() {
        Member inactiveMember = TestFixture.inactiveMember(1L, EMAIL, "encoded-password", NICKNAME);
        when(memberRepository.findByEmail(EMAIL)).thenReturn(Optional.of(inactiveMember));

        assertThatThrownBy(() -> authService.login(EMAIL, PASSWORD))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(MemberErrorCode.INACTIVE_MEMBER));
    }

    @Test
    @DisplayName("[Exception] 비밀번호가 일치하지 않으면 INVALID_CREDENTIALS 예외가 발생한다")
    void login_wrongPassword_throwsException() {
        Member member = TestFixture.member(1L, EMAIL, "encoded-password", NICKNAME);
        when(memberRepository.findByEmail(EMAIL)).thenReturn(Optional.of(member));
        when(passwordEncoder.matches("wrong-password", "encoded-password")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(EMAIL, "wrong-password"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(MemberErrorCode.INVALID_CREDENTIALS));
    }

    @Test
    @DisplayName("[Happy] 로그인에 성공하면 Access Token과 Refresh Token을 발급한다")
    void login_success_returnsTokens() {
        Member member = TestFixture.member(1L, EMAIL, "encoded-password", NICKNAME);
        when(memberRepository.findByEmail(EMAIL)).thenReturn(Optional.of(member));
        when(passwordEncoder.matches(PASSWORD, "encoded-password")).thenReturn(true);
        when(jwtTokenProvider.createAccessToken(1L, EMAIL)).thenReturn("access-token");
        when(jwtTokenProvider.createRefreshToken()).thenReturn("refresh-token");
        when(tokenRepository.save(any(Token.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TokenResult result = authService.login(EMAIL, PASSWORD);

        assertThat(result.accessToken()).isEqualTo("access-token");
        assertThat(result.refreshToken()).isEqualTo("refresh-token");
        verify(tokenRepository).deleteByMemberId(1L);
    }

    @Test
    @DisplayName("[Happy] 로그아웃 시 해당 회원의 Refresh Token을 삭제한다")
    void logout_deletesRefreshToken() {
        authService.logout(1L);

        verify(tokenRepository).deleteByMemberId(1L);
    }

    @Test
    @DisplayName("[Exception] 유효하지 않은 Refresh Token으로 재발급하면 INVALID_REFRESH_TOKEN 예외가 발생한다")
    void reissue_invalidToken_throwsException() {
        when(tokenRepository.findByRefreshToken("invalid")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.reissue("invalid"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(MemberErrorCode.INVALID_REFRESH_TOKEN));
    }

    @Test
    @DisplayName("[Exception] 만료된 Refresh Token으로 재발급하면 EXPIRED_REFRESH_TOKEN 예외가 발생한다")
    void reissue_expiredToken_throwsException() {
        Token expiredToken = TestFixture.token(1L, 1L, "expired-token", LocalDateTime.now().minusMinutes(1));
        when(tokenRepository.findByRefreshToken("expired-token")).thenReturn(Optional.of(expiredToken));

        assertThatThrownBy(() -> authService.reissue("expired-token"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(MemberErrorCode.EXPIRED_REFRESH_TOKEN));
    }

    @Test
    @DisplayName("[Exception] 재발급 시 회원이 존재하지 않으면 MEMBER_NOT_FOUND 예외가 발생한다")
    void reissue_memberNotFound_throwsException() {
        Token validToken = TestFixture.token(1L, 1L, "valid-token", LocalDateTime.now().plusHours(1));
        when(tokenRepository.findByRefreshToken("valid-token")).thenReturn(Optional.of(validToken));
        when(memberRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.reissue("valid-token"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(MemberErrorCode.MEMBER_NOT_FOUND));
    }

    @Test
    @DisplayName("[Exception] 비활성 회원은 Refresh Token 재발급 시 INACTIVE_MEMBER 예외가 발생한다")
    void reissue_inactiveMember_throwsException() {
        Token validToken = TestFixture.token(1L, 1L, "valid-token", LocalDateTime.now().plusHours(1));
        Member inactiveMember = TestFixture.inactiveMember(1L, EMAIL, "encoded-password", NICKNAME);
        when(tokenRepository.findByRefreshToken("valid-token")).thenReturn(Optional.of(validToken));
        when(memberRepository.findById(1L)).thenReturn(Optional.of(inactiveMember));

        assertThatThrownBy(() -> authService.reissue("valid-token"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(MemberErrorCode.INACTIVE_MEMBER));
    }

    @Test
    @DisplayName("[Happy] 유효한 Refresh Token으로 재발급에 성공하면 새 토큰 쌍을 반환한다")
    void reissue_success_returnsNewTokens() {
        Token validToken = TestFixture.token(1L, 1L, "valid-token", LocalDateTime.now().plusHours(1));
        Member member = TestFixture.member(1L, EMAIL, "encoded-password", NICKNAME);

        when(tokenRepository.findByRefreshToken("valid-token")).thenReturn(Optional.of(validToken));
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));
        when(jwtTokenProvider.createAccessToken(anyLong(), anyString())).thenReturn("new-access-token");
        when(jwtTokenProvider.createRefreshToken()).thenReturn("new-refresh-token");
        when(tokenRepository.save(any(Token.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TokenResult result = authService.reissue("valid-token");

        assertThat(result.accessToken()).isEqualTo("new-access-token");
        assertThat(result.refreshToken()).isEqualTo("new-refresh-token");
        verify(tokenRepository).deleteByRefreshToken("valid-token");
    }
}
