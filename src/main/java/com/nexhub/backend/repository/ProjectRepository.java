package com.nexhub.backend.repository;

import com.nexhub.backend.model.Project;
import com.nexhub.backend.model.Tag;
import jakarta.annotation.Nullable;
import org.jspecify.annotations.NullMarked;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface ProjectRepository extends JpaRepository<Project, Long> {

    @Override
    <S extends Project> S save(S s);

    @Override
    Optional<Project> findById(Long aLong);
    Optional<Project> findByName(String name);
    List<Project> findAllByTags(Set<Tag> tags);
}