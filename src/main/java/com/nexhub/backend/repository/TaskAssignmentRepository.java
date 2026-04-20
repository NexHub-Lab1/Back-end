package com.nexhub.backend.repository;

import com.nexhub.backend.model.TaskAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TaskAssignmentRepository extends JpaRepository<TaskAssignment, Long> {
    @Query("select assignment from TaskAssignment assignment where assignment.task.id = :taskId order by assignment.assignedAt desc")
    List<TaskAssignment> findByTaskIdOrderByAssignedAtDesc(@Param("taskId") Long taskId);

    @Query("select assignment from TaskAssignment assignment where assignment.user.id = :userId order by assignment.assignedAt desc")
    List<TaskAssignment> findByUserIdOrderByAssignedAtDesc(@Param("userId") Long userId);

    @Query("""
            select count(assignment) from TaskAssignment assignment
            where assignment.task.id = :taskId
            and lower(assignment.status) = 'active'
            and (:assignmentIdToIgnore is null or assignment.id <> :assignmentIdToIgnore)
            """)
    Long countOtherActiveByTaskId(
            @Param("taskId") Long taskId,
            @Param("assignmentIdToIgnore") Long assignmentIdToIgnore
    );
}
