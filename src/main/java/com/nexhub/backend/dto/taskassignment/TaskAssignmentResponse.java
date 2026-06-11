package com.nexhub.backend.dto.taskassignment;

import com.nexhub.backend.model.Project;
import com.nexhub.backend.model.Task;
import com.nexhub.backend.model.TaskAssignment;
import com.nexhub.backend.model.User;

import java.sql.Date;

public record TaskAssignmentResponse(
        Long id,
        Long taskId,
        String taskTitle,
        Long projectId,
        String projectName,
        Long userId,
        String username,
        Date assignedAt,
        String status,
        Integer attemptsUsed,
        Long parentAssignmentId
) {
    public static TaskAssignmentResponse fromTaskAssignment(TaskAssignment assignment) {
        Task task = assignment.getTask();
        Project project = task != null ? task.getProject() : null;
        User user = assignment.getUser();

        return new TaskAssignmentResponse(
                assignment.getId(),
                task != null ? task.getId() : null,
                task != null ? task.getTitle() : null,
                project != null ? project.getId() : null,
                project != null ? project.getName() : null,
                user != null ? user.getId() : null,
                user != null ? user.getUsername() : null,
                assignment.getAssignedAt(),
                assignment.getStatus(),
                assignment.getAttemptsUsed(),
                assignment.getParentAssignment() != null ? assignment.getParentAssignment().getId() : null
        );
    }
}
