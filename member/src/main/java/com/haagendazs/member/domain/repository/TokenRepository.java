package com.haagendazs.member.domain.repository;

import com.haagendazs.member.domain.model.Token;

import java.util.Optional;

public interface TokenRepository {

    Token save(Token token);

    Optional<Token> findByRefreshToken(String refreshToken);

    void deleteByMemberId(Long memberId);

    void deleteByRefreshToken(String refreshToken);
}
