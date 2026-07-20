package com.nexhub.backend.repository;

import com.nexhub.backend.model.GithubPullRequestComment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GithubPullRequestCommentRepository extends JpaRepository<GithubPullRequestComment, Long> {
    Optional<GithubPullRequestComment> findByEventTypeAndGithubCommentId(String eventType, Long githubCommentId);

    List<GithubPullRequestComment> findTop50BySubmission_Task_IdAndDeletedFalseOrderByGithubCreatedAtDesc(Long taskId);

    List<GithubPullRequestComment> findTop50BySubmission_Task_IdAndSubmission_User_IdAndDeletedFalseOrderByGithubCreatedAtDesc(
            Long taskId,
            Long userId
    );
}
