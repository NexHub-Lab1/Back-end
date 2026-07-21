package com.nexhub.backend.repository;

import com.nexhub.backend.model.Task;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.Optional;

import java.util.List;

public interface TaskRepository extends JpaRepository<Task, Long> {
    boolean existsByProject_Id(Long projectId);

    Optional<Task> findByGithubIssueId(Long githubIssueId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"project", "project.owner"})
    @Query("select task from Task task where task.id = :taskId")
    Optional<Task> findByIdForGithubIssueSync(@Param("taskId") Long taskId);

    @EntityGraph(attributePaths = {"project"})
    @Query("select task from Task task where lower(task.status) <> 'cancelled'")
    Page<Task> findAllVisible(Pageable pageable);

    @EntityGraph(attributePaths = {"project"})
    @Query("SELECT t FROM Task t WHERE " +
           "(:search IS NULL OR :search = '' OR LOWER(t.title) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(t.description) LIKE LOWER(CONCAT('%', :search, '%'))) AND " +
           "((:status IS NULL OR :status = '') AND LOWER(t.status) <> 'cancelled' OR LOWER(t.status) = LOWER(:status))")
    Page<Task> searchTasks(@Param("search") String search, @Param("status") String status, Pageable pageable);

    @EntityGraph(attributePaths = {"project", "project.owner", "recommendedSkills"})
    @Query("""
            select distinct task from Task task
            where lower(task.status) not in ('cancelled', 'completed', 'closed')
            """)
    List<Task> findFeaturedCandidates();

    @EntityGraph(attributePaths = {"project"})
    @Query("select task from Task task where task.project.id = :projectId and lower(task.status) <> 'cancelled'")
    Page<Task> findByProjectId(@Param("projectId") Long projectId, Pageable pageable);

    @EntityGraph(attributePaths = {"project"})
    @Query("select task from Task task where task.project.owner.id = :ownerId and lower(task.status) <> 'cancelled'")
    Page<Task> findByProjectOwnerId(@Param("ownerId") Long ownerId, Pageable pageable);

    @EntityGraph(attributePaths = {"project"})
    @Query("select task from Task task where lower(task.status) = lower(:status)")
    Page<Task> findByStatusIgnoreCase(@Param("status") String status, Pageable pageable);

    @EntityGraph(attributePaths = {"project"})
    @Query("""
            select distinct task from Task task
            join task.recommendedSkills skill
            where lower(skill.name) = lower(:skillName)
            """)
    Page<Task> findDistinctByRecommendedSkills_NameIgnoreCase(@Param("skillName") String skillName, Pageable pageable);
}
