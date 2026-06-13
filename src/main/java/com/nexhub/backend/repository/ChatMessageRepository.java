package com.nexhub.backend.repository;

import com.nexhub.backend.model.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    List<ChatMessage> findByAssignmentIdOrderByCreatedAtAsc(Long assignmentId);
}
