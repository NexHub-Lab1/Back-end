package com.nexhub.backend.dto.taskinvitation;

import com.nexhub.backend.model.TaskInvitation;
import java.sql.Date;

public record TaskInvitationResponse(
        Long id,
        Long taskId,
        String taskTitle,
        Long projectId,
        String projectName,
        Long senderId,
        String senderUsername,
        Long receiverId,
        String receiverUsername,
        String status,
        Date createdAt
) {
    public static TaskInvitationResponse fromTaskInvitation(TaskInvitation invitation) {
        return new TaskInvitationResponse(
                invitation.getId(),
                invitation.getTask() != null ? invitation.getTask().getId() : null,
                invitation.getTask() != null ? invitation.getTask().getTitle() : null,
                (invitation.getTask() != null && invitation.getTask().getProject() != null) ? invitation.getTask().getProject().getId() : null,
                (invitation.getTask() != null && invitation.getTask().getProject() != null) ? invitation.getTask().getProject().getName() : null,
                invitation.getSender() != null ? invitation.getSender().getId() : null,
                invitation.getSender() != null ? invitation.getSender().getUsername() : null,
                invitation.getReceiver() != null ? invitation.getReceiver().getId() : null,
                invitation.getReceiver() != null ? invitation.getReceiver().getUsername() : null,
                invitation.getStatus(),
                invitation.getCreatedAt()
        );
    }
}
