package com.nexhub.backend.repository;

import com.nexhub.backend.model.Project;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProjectRepository extends JpaRepository<Project, Long> {
    @EntityGraph(attributePaths = {"owner"})
    @Query("SELECT p FROM Project p WHERE " +
           "(:search IS NULL OR :search = '' OR LOWER(p.name) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(p.description) LIKE LOWER(CONCAT('%', :search, '%'))) AND " +
           "(:status IS NULL OR :status = '' OR LOWER(p.status) = LOWER(:status))")
    Page<Project> searchProjects(@Param("search") String search, @Param("status") String status, Pageable pageable);
    Optional<Project> findByName(String name);

    @EntityGraph(attributePaths = {"owner"})
    Page<Project> findDistinctByTags_NameIgnoreCase(String tagName, Pageable pageable);

    @EntityGraph(attributePaths = {"owner"})
    Page<Project> findByOwner_Id(Long id, Pageable pageable);

    List<Project> findByOwner_Id(Long id);

    @EntityGraph(attributePaths = {"owner"})
    Page<Project> findAll(Pageable pageable);

    boolean existsByOwner_Id(Long ownerId);
    boolean existsByContributors_Id(Long contributorId);
}
