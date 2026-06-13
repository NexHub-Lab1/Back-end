package com.nexhub.backend.service;

import com.nexhub.backend.model.Notification;
import com.nexhub.backend.model.User;
import com.nexhub.backend.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationService {
    private final NotificationRepository notificationRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final EmailService emailService;

    @Transactional
    public void sendNotification(User user, String message, String type) {
        sendNotification(user, message, type, null);
    }

    @Transactional
    public void sendNotification(User user, String message, String type, String targetPath) {
        Notification notification = new Notification();
        notification.setUser(user);
        notification.setMessage(message);
        notification.setType(type);
        notification.setTargetPath(targetPath);
        notification.setRead(false);

        Notification savedNotification = notificationRepository.save(notification);

        // Send real-time notification via WebSocket
        // Destination: /user/{email}/queue/notifications
        // We use email as the principal name from the AuthChannelInterceptor
        messagingTemplate.convertAndSendToUser(
                user.getEmail(),
                "/queue/notifications",
                savedNotification
        );

        // Send email notification if user enabled them
        if (user.isEmailNotificationsEnabled() && user.getEmail() != null) {
            emailService.sendNotificationEmail(
                    user.getEmail(),
                    "NexHub Alert: " + type,
                    message,
                    type,
                    targetPath
            );
        }
    }

    public List<Notification> getNotificationsForUser(User user) {
        return notificationRepository.findByUserOrderByCreatedAtDesc(user);
    }

    @Transactional
    public void markAsRead(Long notificationId) {
        notificationRepository.findById(notificationId).ifPresent(notification -> {
            notification.setRead(true);
            notificationRepository.save(notification);
        });
    }
}
