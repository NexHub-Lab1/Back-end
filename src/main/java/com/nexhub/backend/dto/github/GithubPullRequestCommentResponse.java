package com.nexhub.backend.dto.github;

import com.nexhub.backend.model.GithubPullRequestComment;
import com.nexhub.backend.model.TaskSubmission;

import java.sql.Timestamp;

public record GithubPullRequestCommentResponse(
        Long id,
        Long submissionId,
        Long taskId,
        String pullRequestUrl,
        String eventType,
        String authorUsername,
        String authorAvatarUrl,
        String body,
        String githubUrl,
        Timestamp createdAt,
        Timestamp updatedAt
) {
    public static GithubPullRequestCommentResponse fromComment(GithubPullRequestComment comment) {
        TaskSubmission submission = comment.getSubmission();
        return new GithubPullRequestCommentResponse(
                comment.getId(),
                submission == null ? null : submission.getId(),
                submission == null || submission.getTask() == null ? null : submission.getTask().getId(),
                submission == null ? null : submission.getPullRequestUrl(),
                comment.getEventType(),
                comment.getAuthorUsername(),
                comment.getAuthorAvatarUrl(),
                comment.getBody(),
                comment.getGithubUrl(),
                comment.getGithubCreatedAt(),
                comment.getGithubUpdatedAt()
        );
    }
}
