package com.nexhub.backend.repository;

import com.nexhub.backend.model.Project;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProjectRepository extends JpaRepository<Project, Long> {
    Optional<Project> findByName(String name);

    Page<Project> findDistinctByTags_NameIgnoreCase(String tagName, Pageable pageable);
    Page<Project> findByOwner_Id(Long id, Pageable pageable);
    boolean existsByOwner_Id(Long ownerId);
    boolean existsByContributors_Id(Long contributorId);
}
