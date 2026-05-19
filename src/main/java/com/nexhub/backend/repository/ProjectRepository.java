package com.nexhub.backend.repository;

import com.nexhub.backend.model.Project;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProjectRepository extends JpaRepository<Project, Long> {
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
