package com.nexhub.backend.service;

import com.nexhub.backend.dto.chat.ChatMessageResponse;
import com.nexhub.backend.model.ChatMessage;
import com.nexhub.backend.model.TaskAssignment;
import com.nexhub.backend.model.User;
import com.nexhub.backend.repository.ChatMessageRepository;
import com.nexhub.backend.repository.TaskAssignmentRepository;
import com.nexhub.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
@Transactional
public class ChatMessageService {
    private final ChatMessageRepository chatMessageRepository;
    private final TaskAssignmentRepository taskAssignmentRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final NotificationService notificationService;


    @Transactional(readOnly = true)
    public List<ChatMessageResponse> getChatHistory(Long assignmentId) {
        return chatMessageRepository.findByAssignmentIdOrderByCreatedAtAsc(assignmentId)
                .stream()
                .map(ChatMessageResponse::fromMessage)
                .toList();
    }

    public ChatMessageResponse sendMessage(Long assignmentId, Long senderId, String content) {
        TaskAssignment assignment = taskAssignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new NoSuchElementException("Assignment not found"));

        User sender = userRepository.findById(senderId)
                .orElseThrow(() -> new NoSuchElementException("User not found"));

        // Security check: Only assignee, task project owner, or project contributors can send messages
        boolean isAssignee = assignment.getUser().getId().equals(senderId);
        boolean isOwner = assignment.getTask().getProject().getOwner().getId().equals(senderId);
        boolean isContributor = assignment.getTask().getProject().getContributors().stream()
                .anyMatch(u -> u.getId().equals(senderId));
        
        if (!isAssignee && !isOwner && !isContributor) {
            throw new IllegalArgumentException("Unauthorized to send message in this chat thread");
        }

        ChatMessage message = new ChatMessage();
        message.setAssignment(assignment);
        message.setSender(sender);
        message.setContent(content.trim());
        ChatMessage saved = chatMessageRepository.save(message);

        ChatMessageResponse response = ChatMessageResponse.fromMessage(saved);
        
        // Push in real-time to WebSocket subscribers of this assignment
        messagingTemplate.convertAndSend("/topic/chat/" + assignmentId, response);

        // Send real-time notification to the recipient (the counterparty of the conversation)
        User recipient = isAssignee ? assignment.getTask().getProject().getOwner() : assignment.getUser();
        String trimmedMsg = content.trim();
        String preview = trimmedMsg.length() > 50 ? trimmedMsg.substring(0, 47) + "..." : trimmedMsg;
        String notificationMsg = sender.getUsername() + " sent you a message on '" + assignment.getTask().getTitle() + "': " + preview;
        notificationService.sendNotification(recipient, notificationMsg, "INFO");

        return response;
    }
}
