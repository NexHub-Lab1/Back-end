package com.nexhub.backend.repository;

import com.nexhub.backend.model.Project;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProjectRepository extends JpaRepository<Project, Long> {
    @Override
    Optional<Project> findById(Long aLong);

    @Override
    <S extends Project> S save(S s);

    Optional<Project> findByName(String name);
}