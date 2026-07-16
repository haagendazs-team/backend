package com.haagendazs.member.infrastructure.persistence;

import com.haagendazs.member.domain.model.Token;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TokenJpaRepository extends JpaRepository<Token, Long> {

    Optional<Token> findByRefreshToken(String refreshToken);

    void deleteByMemberId(Long memberId);

    void deleteByRefreshToken(String refreshToken);
}
