package com.nexhub.backend.repository;

import com.nexhub.backend.model.TaskAssignment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TaskAssignmentRepository extends JpaRepository<TaskAssignment, Long> {
    boolean existsByTask_Id(Long taskId);
    boolean existsByUser_Id(Long userId);

    @Query("select assignment from TaskAssignment assignment where assignment.task.id = :taskId")
    Page<TaskAssignment> findByTaskId(@Param("taskId") Long taskId, Pageable pageable);

    @Query("select assignment from TaskAssignment assignment where assignment.user.id = :userId")
    Page<TaskAssignment> findByUserId(@Param("userId") Long userId, Pageable pageable);

    @Query("""
            select assignment from TaskAssignment assignment
            where assignment.user.id = :userId
            and lower(assignment.status) not in ('completed', 'cancelled')
            """)
    Page<TaskAssignment> findOpenByUserId(@Param("userId") Long userId, Pageable pageable);

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
