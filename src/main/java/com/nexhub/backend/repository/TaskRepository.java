package com.nexhub.backend.repository;

import com.nexhub.backend.model.Task;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TaskRepository extends JpaRepository<Task, Long> {
    boolean existsByProject_Id(Long projectId);

    @Query("select task from Task task where lower(task.status) <> 'cancelled'")
    Page<Task> findAllVisible(Pageable pageable);

    @Query("select task from Task task where task.project.id = :projectId")
    Page<Task> findByProjectId(@Param("projectId") Long projectId, Pageable pageable);

    @Query("select task from Task task where task.project.owner.id = :ownerId")
    Page<Task> findByProjectOwnerId(@Param("ownerId") Long ownerId, Pageable pageable);

    @Query("select task from Task task where lower(task.status) = lower(:status)")
    Page<Task> findByStatusIgnoreCase(@Param("status") String status, Pageable pageable);

    @Query("""
            select distinct task from Task task
            join task.recommendedSkills skill
            where lower(skill.name) = lower(:skillName)
            """)
    Page<Task> findDistinctByRecommendedSkills_NameIgnoreCase(@Param("skillName") String skillName, Pageable pageable);
}
