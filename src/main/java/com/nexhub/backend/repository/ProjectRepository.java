package com.nexhub.backend.repository;

import com.nexhub.backend.model.Project;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProjectRepository extends JpaRepository<Project, Long> {
    Optional<Project> findByName(String name);

    List<Project> findDistinctByTags_NameIgnoreCase(String tagName);
    List<Project> findByOwner_Id(Long id);
    boolean existsByOwner_Id(Long ownerId);
    boolean existsByContributors_Id(Long contributorId);
}
