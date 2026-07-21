package com.nexhub.backend.dto.tasksubmission;

import com.nexhub.backend.model.Project;
import com.nexhub.backend.model.Task;
import com.nexhub.backend.model.TaskAssignment;
import com.nexhub.backend.model.TaskSubmission;
import com.nexhub.backend.model.User;

import java.sql.Date;

public record TaskSubmissionResponse(
        Long id,
        Long taskId,
        String taskTitle,
        Long assignmentId,
        Long projectId,
        String projectName,
        Long userId,
        String username,
        String pullRequestUrl,
        String designUrl,
        String description,
        String demoUrl,
        Date submittedAt,
        String status,
        String reviewComments,
        Date reviewedAt,
        Long reviewerId,
        String reviewerUsername,
        Integer attemptsUsed
) {
    public static TaskSubmissionResponse fromTaskSubmission(TaskSubmission submission) {
        Task task = submission.getTask();
        TaskAssignment assignment = submission.getAssignment();
        Project project = task != null ? task.getProject() : null;
        User user = submission.getUser();
        User reviewer = submission.getReviewer();

        return new TaskSubmissionResponse(
                submission.getId(),
                task != null ? task.getId() : null,
                task != null ? task.getTitle() : null,
                assignment != null ? assignment.getId() : null,
                project != null ? project.getId() : null,
                project != null ? project.getName() : null,
                user != null ? user.getId() : null,
                user != null ? user.getUsername() : null,
                submission.getPullRequestUrl(),
                submission.getDesignUrl(),
                submission.getDescription(),
                submission.getDemoUrl(),
                submission.getSubmittedAt(),
                submission.getStatus(),
                submission.getReviewComments(),
                submission.getReviewedAt(),
                reviewer != null ? reviewer.getId() : null,
                reviewer != null ? reviewer.getUsername() : null,
                submission.getAttemptsUsed()
        );
    }
}
