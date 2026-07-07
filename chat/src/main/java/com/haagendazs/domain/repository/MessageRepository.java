package com.haagendazs.domain.repository;

import com.haagendazs.domain.model.Message;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MessageRepository extends JpaRepository<Message, Long> {

}
