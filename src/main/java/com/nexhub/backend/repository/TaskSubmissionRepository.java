package com.nexhub.backend.repository;

import com.nexhub.backend.model.TaskSubmission;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Optional;

public interface TaskSubmissionRepository extends JpaRepository<TaskSubmission, Long> {
    boolean existsByTask_Id(Long taskId);
    boolean existsByUser_Id(Long userId);
    boolean existsByReviewer_Id(Long reviewerId);

    @EntityGraph(attributePaths = {"task", "task.project", "user", "assignment", "reviewer"})
    @Query("select submission from TaskSubmission submission where submission.task.id = :taskId")
    Page<TaskSubmission> findByTaskId(@Param("taskId") Long taskId, Pageable pageable);

    @EntityGraph(attributePaths = {"task", "task.project", "user", "assignment", "reviewer"})
    @Query("select submission from TaskSubmission submission where submission.assignment.id = :assignmentId")
    Page<TaskSubmission> findByAssignmentId(@Param("assignmentId") Long assignmentId, Pageable pageable);

    @EntityGraph(attributePaths = {"task", "task.project", "user", "assignment", "reviewer"})
    @Query("select submission from TaskSubmission submission where submission.user.id = :userId")
    Page<TaskSubmission> findByUserId(@Param("userId") Long userId, Pageable pageable);

    @EntityGraph(attributePaths = {"task", "task.project", "user", "assignment", "reviewer"})
    @Query("select submission from TaskSubmission submission where submission.task.project.owner.id = :reviewerId")
    Page<TaskSubmission> findByProjectOwnerId(@Param("reviewerId") Long reviewerId, Pageable pageable);

    @EntityGraph(attributePaths = {"task", "task.project", "user", "assignment", "reviewer"})
    @Query("""
            select submission from TaskSubmission submission
            where submission.task.project.owner.id = :reviewerId
            and lower(submission.status) = lower(:status)
            """)
    Page<TaskSubmission> findByProjectOwnerIdAndStatus(
            @Param("reviewerId") Long reviewerId,
            @Param("status") String status,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"task", "task.project", "task.project.owner", "user", "assignment", "reviewer"})
    @Query("select submission from TaskSubmission submission where lower(submission.pullRequestUrl) in :pullRequestUrls")
    Optional<TaskSubmission> findFirstByPullRequestUrlNormalizedIn(
            @Param("pullRequestUrls") Collection<String> pullRequestUrls
    );
}
