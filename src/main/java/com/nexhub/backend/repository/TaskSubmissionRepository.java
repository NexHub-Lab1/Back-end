package com.nexhub.backend.repository;

import com.nexhub.backend.model.TaskSubmission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TaskSubmissionRepository extends JpaRepository<TaskSubmission, Long> {
    @Query("select submission from TaskSubmission submission where submission.task.id = :taskId order by submission.submittedAt desc")
    List<TaskSubmission> findByTaskIdOrderBySubmittedAtDesc(@Param("taskId") Long taskId);

    @Query("select submission from TaskSubmission submission where submission.assignment.id = :assignmentId order by submission.submittedAt desc")
    List<TaskSubmission> findByAssignmentIdOrderBySubmittedAtDesc(@Param("assignmentId") Long assignmentId);

    @Query("select submission from TaskSubmission submission where submission.user.id = :userId order by submission.submittedAt desc")
    List<TaskSubmission> findByUserIdOrderBySubmittedAtDesc(@Param("userId") Long userId);
}
