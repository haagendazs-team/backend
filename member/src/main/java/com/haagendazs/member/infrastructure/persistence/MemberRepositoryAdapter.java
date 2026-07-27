package com.haagendazs.member.infrastructure.persistence;

import com.haagendazs.member.domain.model.Member;
import com.haagendazs.member.domain.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class MemberRepositoryAdapter implements MemberRepository {

    private final MemberJpaRepository jpaRepository;

    @Override
    public Member save(Member member) {
        return jpaRepository.save(member);
    }

    @Override
    public Optional<Member> findById(Long memberId) {
        return jpaRepository.findById(memberId);
    }

    @Override
    public Optional<Member> findByEmail(String email) {
        return jpaRepository.findByEmail(email);
    }

    @Override
    public boolean existsByEmail(String email) {
        return jpaRepository.existsByEmail(email);
    }

    @Override
    public boolean existsActiveById(Long memberId) {
        return jpaRepository.existsByMemberIdAndIsActiveTrue(memberId);
    }
}
