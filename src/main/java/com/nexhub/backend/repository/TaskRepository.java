package com.nexhub.backend.repository;

import com.nexhub.backend.model.Task;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TaskRepository extends JpaRepository<Task, Long> {
    List<Task> findByProjectIdOrderByCreated_atDesc(Long projectId);

    List<Task> findByStatusIgnoreCaseOrderByCreated_atDesc(String status);

    List<Task> findDistinctByRecommendedSkills_NameIgnoreCaseOrderByCreated_atDesc(String skillName);
}
