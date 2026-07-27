package com.haagendazs.member.domain.repository;

import com.haagendazs.member.domain.model.Member;

import java.util.Optional;

public interface MemberRepository {

    Member save(Member member);

    Optional<Member> findById(Long memberId);

    Optional<Member> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsActiveById(Long memberId);

}
