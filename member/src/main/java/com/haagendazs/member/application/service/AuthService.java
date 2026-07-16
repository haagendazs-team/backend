package com.haagendazs.member.application.service;

import com.haagendazs.common.exception.BusinessException;
import com.haagendazs.member.application.dto.MemberResult;
import com.haagendazs.member.application.dto.TokenResult;
import com.haagendazs.member.domain.exception.MemberErrorCode;
import com.haagendazs.member.domain.model.Member;
import com.haagendazs.member.domain.model.Token;
import com.haagendazs.member.application.port.MemberEventPublisher;
import com.haagendazs.member.application.port.NotificationEventPublisher;
import com.haagendazs.member.domain.repository.MemberRepository;
import com.haagendazs.member.domain.repository.TokenRepository;
import com.haagendazs.member.infrastructure.config.JwtProperties;
import com.haagendazs.member.infrastructure.kafka.EmailVerificationCodeGenerator;
import com.haagendazs.member.infrastructure.kafka.TransactionAfterCommitExecutor;
import com.haagendazs.member.infrastructure.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private final MemberRepository memberRepository;
    private final TokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final JwtProperties jwtProperties;
    private final MemberEventPublisher memberEventPublisher;
    private final NotificationEventPublisher notificationEventPublisher;
    private final EmailVerificationCodeGenerator emailVerificationCodeGenerator;
    private final TransactionAfterCommitExecutor afterCommitExecutor;

    @Transactional
    public MemberResult signup(String email, String password, String nickname) {
        if (memberRepository.existsByEmail(email)) {
            throw new BusinessException(MemberErrorCode.EMAIL_ALREADY_EXISTS);
        }

        Member member = Member.create(email, passwordEncoder.encode(password), nickname);
        Member saved = memberRepository.save(member);
        String verificationCode = emailVerificationCodeGenerator.generate();
        afterCommitExecutor.runAfterCommit(() -> {
            notificationEventPublisher.publishEmailCert(email, verificationCode);
            memberEventPublisher.publishCreated(saved);
        });
        return MemberResult.from(saved);
    }

    @Transactional
    public TokenResult login(String email, String password) {
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(MemberErrorCode.INVALID_CREDENTIALS));

        validateActiveMember(member);

        if (!passwordEncoder.matches(password, member.getPassword())) {
            throw new BusinessException(MemberErrorCode.INVALID_CREDENTIALS);
        }

        return issueToken(member);
    }

    @Transactional
    public void logout(Long memberId) {
        tokenRepository.deleteByMemberId(memberId);
    }

    @Transactional
    public TokenResult reissue(String refreshToken) {
        Token token = tokenRepository.findByRefreshToken(refreshToken)
                .orElseThrow(() -> new BusinessException(MemberErrorCode.INVALID_REFRESH_TOKEN));

        if (token.isExpired()) {
            tokenRepository.deleteByRefreshToken(refreshToken);
            throw new BusinessException(MemberErrorCode.EXPIRED_REFRESH_TOKEN);
        }

        Member member = memberRepository.findById(token.getMemberId())
                .orElseThrow(() -> new BusinessException(MemberErrorCode.MEMBER_NOT_FOUND));

        validateActiveMember(member);
        tokenRepository.deleteByRefreshToken(refreshToken);

        return issueToken(member);
    }

    private TokenResult issueToken(Member member) {
        String accessToken = jwtTokenProvider.createAccessToken(member.getMemberId(), member.getEmail());
        String refreshToken = jwtTokenProvider.createRefreshToken();
        LocalDateTime expiredAt = LocalDateTime.now().plusSeconds(jwtProperties.getRefreshExpirationMs() / 1000);

        tokenRepository.deleteByMemberId(member.getMemberId());
        tokenRepository.save(Token.create(member.getMemberId(), refreshToken, expiredAt));

        return new TokenResult(accessToken, refreshToken);
    }

    private void validateActiveMember(Member member) {
        if (!Boolean.TRUE.equals(member.getIsActive())) {
            throw new BusinessException(MemberErrorCode.INACTIVE_MEMBER);
        }
    }
}
