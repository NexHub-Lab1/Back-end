package com.nexhub.backend.service;

import com.nexhub.backend.model.ChatMessage;
import com.nexhub.backend.model.Project;
import com.nexhub.backend.model.Task;
import com.nexhub.backend.model.TaskAssignment;
import com.nexhub.backend.model.User;
import com.nexhub.backend.repository.ChatMessageRepository;
import com.nexhub.backend.repository.TaskAssignmentRepository;
import com.nexhub.backend.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatMessageServiceTest {
    @Mock
    private ChatMessageRepository chatMessageRepository;

    @Mock
    private TaskAssignmentRepository taskAssignmentRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private ChatMessageService chatMessageService;

    @Test
    void chatNotificationLinksDirectlyToConversation() {
        User owner = user(1L, "owner");
        User developer = user(2L, "developer");

        Project project = new Project();
        project.setOwner(owner);

        Task task = new Task();
        task.setProject(project);
        task.setTitle("Review webhook activity");

        TaskAssignment assignment = new TaskAssignment();
        setField(assignment, "id", 30L);
        assignment.setTask(task);
        assignment.setUser(developer);

        when(taskAssignmentRepository.findById(30L)).thenReturn(Optional.of(assignment));
        when(userRepository.findById(1L)).thenReturn(Optional.of(owner));
        when(chatMessageRepository.save(any(ChatMessage.class))).thenAnswer(invocation -> {
            ChatMessage message = invocation.getArgument(0);
            setField(message, "id", 50L);
            message.setCreatedAt(LocalDateTime.now());
            return message;
        });

        chatMessageService.sendMessage(30L, 1L, "Please check the latest feedback");

        verify(notificationService).sendNotification(
                eq(developer),
                contains("sent you a message"),
                eq("INFO"),
                eq("/profile?tab=chats&assignmentId=30")
        );
    }

    private static User user(Long id, String username) {
        User user = new User();
        setField(user, "id", id);
        user.setUsername(username);
        user.setEmail(username + "@nexhub.dev");
        return user;
    }

    private static void setField(Object target, String name, Object value) {
        try {
            var field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }
}
