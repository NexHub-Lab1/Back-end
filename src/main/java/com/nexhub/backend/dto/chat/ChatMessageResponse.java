package com.nexhub.backend.dto.chat;

import com.nexhub.backend.model.ChatMessage;

public record ChatMessageResponse(
    Long id,
    Long assignmentId,
    Long senderId,
    String senderUsername,
    String content,
    String createdAt
) {
    public static ChatMessageResponse fromMessage(ChatMessage msg) {
        return new ChatMessageResponse(
            msg.getId(),
            msg.getAssignment().getId(),
            msg.getSender().getId(),
            msg.getSender().getUsername(),
            msg.getContent(),
            msg.getCreatedAt().toString()
        );
    }
}
