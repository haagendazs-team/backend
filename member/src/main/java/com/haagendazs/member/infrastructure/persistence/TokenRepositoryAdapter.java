package com.haagendazs.member.infrastructure.persistence;

import com.haagendazs.member.domain.model.Token;
import com.haagendazs.member.domain.repository.TokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class TokenRepositoryAdapter implements TokenRepository {

    private final TokenJpaRepository jpaRepository;

    @Override
    public Token save(Token token) {
        return jpaRepository.save(token);
    }

    @Override
    public Optional<Token> findByRefreshToken(String refreshToken) {
        return jpaRepository.findByRefreshToken(refreshToken);
    }

    @Override
    public void deleteByMemberId(Long memberId) {
        jpaRepository.deleteByMemberId(memberId);
    }

    @Override
    public void deleteByRefreshToken(String refreshToken) {
        jpaRepository.deleteByRefreshToken(refreshToken);
    }
}
