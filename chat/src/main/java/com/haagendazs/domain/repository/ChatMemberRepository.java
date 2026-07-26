package com.haagendazs.domain.repository;

import com.haagendazs.domain.model.ChatMember;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatMemberRepository  extends JpaRepository<ChatMember, Long> {
}
