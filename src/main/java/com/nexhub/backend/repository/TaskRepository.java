package com.nexhub.backend.repository;

import com.nexhub.backend.model.Task;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TaskRepository extends JpaRepository<Task, Long> {
    @Query("select task from Task task where task.project.id = :projectId order by task.created_at desc")
    List<Task> findByProjectIdOrderByCreated_atDesc(@Param("projectId") Long projectId);

    @Query("select task from Task task where lower(task.status) = lower(:status) order by task.created_at desc")
    List<Task> findByStatusIgnoreCaseOrderByCreated_atDesc(@Param("status") String status);

    @Query("""
            select distinct task from Task task
            join task.recommendedSkills skill
            where lower(skill.name) = lower(:skillName)
            order by task.created_at desc
            """)
    List<Task> findDistinctByRecommendedSkills_NameIgnoreCaseOrderByCreated_atDesc(@Param("skillName") String skillName);
}
