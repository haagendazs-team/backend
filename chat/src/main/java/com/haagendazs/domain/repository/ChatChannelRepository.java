package com.haagendazs.domain.repository;

import com.haagendazs.domain.model.ChatChannel;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ChatChannelRepository extends JpaRepository<ChatChannel, Long> {
    Optional<ChatChannel> findByDmKey(String dmKey);


}
