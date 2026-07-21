package com.nexhub.backend.service;

import com.nexhub.backend.dto.github.GithubPullRequestCommentResponse;
import com.nexhub.backend.model.GithubPullRequestComment;
import com.nexhub.backend.model.Task;
import com.nexhub.backend.model.TaskSubmission;
import com.nexhub.backend.model.User;
import com.nexhub.backend.repository.GithubPullRequestCommentRepository;
import com.nexhub.backend.repository.TaskAssignmentRepository;
import com.nexhub.backend.repository.TaskRepository;
import com.nexhub.backend.repository.TaskSubmissionRepository;
import com.nexhub.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class GithubActivityService {
    private static final int MAX_COMMENT_LENGTH = 20_000;

    private final GithubPullRequestCommentRepository commentRepository;
    private final TaskSubmissionRepository taskSubmissionRepository;
    private final TaskRepository taskRepository;
    private final TaskAssignmentRepository taskAssignmentRepository;
    private final UserRepository userRepository;

    public enum CommentResult {
        CREATED,
        UPDATED,
        DELETED,
        DUPLICATE
    }

    @Transactional
    public CommentResult applyComment(
            TaskSubmission submission,
            String eventType,
            String action,
            String deliveryId,
            Long githubCommentId,
            String authorUsername,
            String authorAvatarUrl,
            String body,
            String githubUrl,
            Timestamp createdAt,
            Timestamp updatedAt
    ) {
        if (submission == null || githubCommentId == null || eventType == null || eventType.isBlank()) {
            throw new IllegalArgumentException("A linked submission and GitHub comment are required");
        }

        Optional<GithubPullRequestComment> stored = commentRepository
                .findByEventTypeAndGithubCommentId(eventType, githubCommentId);
        if (stored.isPresent() && sameDelivery(stored.get().getLastDeliveryId(), deliveryId)) {
            return CommentResult.DUPLICATE;
        }

        GithubPullRequestComment comment = stored.orElseGet(GithubPullRequestComment::new);
        boolean newComment = stored.isEmpty();
        comment.setSubmission(submission);
        comment.setEventType(eventType);
        comment.setGithubCommentId(githubCommentId);
        comment.setLastDeliveryId(blankToNull(deliveryId));
        comment.setAuthorUsername(authorUsername);
        comment.setAuthorAvatarUrl(authorAvatarUrl);
        comment.setBody(trimBody(body));
        comment.setGithubUrl(githubUrl);
        comment.setGithubCreatedAt(createdAt);
        comment.setGithubUpdatedAt(updatedAt != null ? updatedAt : createdAt);
        comment.setDeleted("deleted".equalsIgnoreCase(action));
        comment.setReceivedAt(Timestamp.from(Instant.now()));
        commentRepository.save(comment);

        if (Boolean.TRUE.equals(comment.getDeleted())) {
            return CommentResult.DELETED;
        }
        return newComment && "created".equalsIgnoreCase(action)
                ? CommentResult.CREATED
                : CommentResult.UPDATED;
    }

    @Transactional
    public boolean updateReviewState(
            TaskSubmission submission,
            String deliveryId,
            String state,
            String author,
            String reviewUrl,
            Timestamp updatedAt
    ) {
        if (submission == null) {
            return false;
        }
        if (sameDelivery(submission.getGithubReviewLastDeliveryId(), deliveryId)) {
            return false;
        }
        if (updatedAt != null && submission.getGithubReviewUpdatedAt() != null
                && updatedAt.before(submission.getGithubReviewUpdatedAt())) {
            return false;
        }

        submission.setGithubReviewState(state);
        submission.setGithubReviewAuthor(author);
        submission.setGithubReviewUrl(reviewUrl);
        submission.setGithubReviewUpdatedAt(updatedAt != null ? updatedAt : Timestamp.from(Instant.now()));
        submission.setGithubReviewLastDeliveryId(blankToNull(deliveryId));
        taskSubmissionRepository.save(submission);
        return true;
    }

    @Transactional(readOnly = true)
    public List<GithubPullRequestCommentResponse> getTaskActivity(Long taskId, String authenticatedEmail) {
        if (authenticatedEmail == null || authenticatedEmail.isBlank()) {
            throw new AccessDeniedException("Authentication required");
        }

        User actor = userRepository.findByEmail(authenticatedEmail)
                .orElseThrow(() -> new AccessDeniedException("Authenticated user not found"));
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new NoSuchElementException("Task not found"));

        boolean isOwner = task.getProject() != null
                && task.getProject().getOwner() != null
                && actor.getId().equals(task.getProject().getOwner().getId());
        boolean isAssigned = taskAssignmentRepository.existsByTask_IdAndUser_Id(taskId, actor.getId());
        boolean hasSubmission = taskSubmissionRepository.existsByTask_IdAndUser_Id(taskId, actor.getId());
        if (!isOwner && !isAssigned && !hasSubmission) {
            throw new AccessDeniedException("GitHub activity is available only to the project owner and assigned developers");
        }

        List<GithubPullRequestComment> comments = isOwner
                ? commentRepository.findTop50BySubmission_Task_IdAndDeletedFalseOrderByGithubCreatedAtDesc(taskId)
                : commentRepository.findTop50BySubmission_Task_IdAndSubmission_User_IdAndDeletedFalseOrderByGithubCreatedAtDesc(
                        taskId,
                        actor.getId()
                );
        return comments.stream().map(GithubPullRequestCommentResponse::fromComment).toList();
    }

    private boolean sameDelivery(String storedDeliveryId, String incomingDeliveryId) {
        return incomingDeliveryId != null
                && !incomingDeliveryId.isBlank()
                && incomingDeliveryId.equals(storedDeliveryId);
    }

    private String trimBody(String body) {
        if (body == null) {
            return "";
        }
        return body.length() <= MAX_COMMENT_LENGTH ? body : body.substring(0, MAX_COMMENT_LENGTH);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
