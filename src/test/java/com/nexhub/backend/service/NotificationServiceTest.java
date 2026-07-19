package com.nexhub.backend.service;

import com.nexhub.backend.model.Notification;
import com.nexhub.backend.model.User;
import com.nexhub.backend.repository.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private EmailService emailService;

    @InjectMocks
    private NotificationService notificationService;

    @Test
    void sendNotificationSavesAndSends() {
        User user = new User();
        user.setEmail("test@example.com");
        String message = "Hello";
        String type = "INFO";

        when(notificationRepository.save(any(Notification.class))).thenAnswer(i -> i.getArgument(0));

        notificationService.sendNotification(user, message, type);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        Notification saved = captor.getValue();

        assertThat(saved.getUser()).isEqualTo(user);
        assertThat(saved.getMessage()).isEqualTo(message);
        assertThat(saved.getType()).isEqualTo(type);
        assertThat(saved.isRead()).isFalse();

        verify(messagingTemplate).convertAndSendToUser(
                eq("test@example.com"),
                eq("/queue/notifications"),
                any(Notification.class)
        );
    }

    @Test
    void sendNotificationIncludesNavigationTargetWhenProvided() {
        User user = new User();
        user.setEmail("test@example.com");

        when(notificationRepository.save(any(Notification.class))).thenAnswer(i -> i.getArgument(0));

        notificationService.sendNotification(user, "Task assigned", "INFO", "/task/42");

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        assertThat(captor.getValue().getTargetPath()).isEqualTo("/task/42");
    }

    @Test
    void sendNotificationSendsEmailWhenAlertsAreEnabled() {
        User user = new User();
        user.setEmail("test@example.com");
        user.setEmailNotificationsEnabled(true);

        when(notificationRepository.save(any(Notification.class))).thenAnswer(i -> i.getArgument(0));

        notificationService.sendNotification(user, "Task assigned", "INFO", "/task/42");

        verify(emailService).sendNotificationEmail(
                eq("test@example.com"),
                eq("NexHub Alert: INFO"),
                eq("Task assigned"),
                eq("INFO"),
                eq("/task/42")
        );
    }

    @Test
    void sendNotificationSkipsEmailWhenAlertsAreDisabled() {
        User user = new User();
        user.setEmail("test@example.com");
        user.setEmailNotificationsEnabled(false);

        when(notificationRepository.save(any(Notification.class))).thenAnswer(i -> i.getArgument(0));

        notificationService.sendNotification(user, "Task assigned", "INFO", "/task/42");

        verify(emailService, never()).sendNotificationEmail(any(), any(), any(), any(), any());
    }
}
