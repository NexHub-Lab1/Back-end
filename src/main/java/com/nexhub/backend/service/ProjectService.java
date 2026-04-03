package com.nexhub.backend.service;

import com.nexhub.backend.dto.project.ProjectRequest;
import com.nexhub.backend.dto.project.ProjectResponse;
import com.nexhub.backend.dto.project.ProjectUpdateRequest;
import com.nexhub.backend.model.Project;
import com.nexhub.backend.model.Tag;
import com.nexhub.backend.model.User;
import com.nexhub.backend.repository.ProjectRepository;
import com.nexhub.backend.repository.TagRepository;
import com.nexhub.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional
public class ProjectService {
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final TagRepository tagRepository;

    @Transactional(readOnly = true)
    public List<ProjectResponse> getAllProjects() {
        return projectRepository.findAll().stream()
                .map(ProjectResponse::fromProject)
                .toList();
    }

    @Transactional(readOnly = true)
    public ProjectResponse getProjectById(Long id) {
        return ProjectResponse.fromProject(findExistingProject(id));
    }

    @Transactional(readOnly = true)
    public List<ProjectResponse> getProjectsByTag(String tag) {
        if (tag == null || tag.isBlank()) {
            throw new IllegalArgumentException("Tag is required");
        }

        return projectRepository.findDistinctByTags_NameIgnoreCase(tag.trim()).stream()
                .map(ProjectResponse::fromProject)
                .toList();
    }

    public ProjectResponse createProject(ProjectRequest request) {
        validateCreateRequest(request);

        User owner = userRepository.findById(request.ownerId())
                .orElseThrow(() -> new NoSuchElementException("Owner not found"));

        Project project = new Project();
        project.setOwner(owner);
        project.setName(request.name().trim());
        project.setDescription(request.description().trim());
        project.setGithubRepo(request.githubRepo().trim());
        project.setStatus(normalizeStatus(request.status()));
        project.setCreated_at(now());
        project.setUpdated_at(now());
        project.setLast_active_at(now());
        project.setCompleted_tasks_count(0L);
        project.setStars_count(0L);
        project.setTags(resolveTags(request.tags()));

        return ProjectResponse.fromProject(projectRepository.save(project));
    }

    public ProjectResponse updateProject(ProjectUpdateRequest request) {
        if (request == null || request.id() == null) {
            throw new IllegalArgumentException("Project id is required");
        }

        Project project = findExistingProject(request.id());

        if (request.name() != null && !request.name().isBlank()) {
            project.setName(request.name().trim());
        }
        if (request.description() != null && !request.description().isBlank()) {
            project.setDescription(request.description().trim());
        }
        if (request.githubRepo() != null && !request.githubRepo().isBlank()) {
            project.setGithubRepo(request.githubRepo().trim());
        }
        if (request.status() != null && !request.status().isBlank()) {
            project.setStatus(request.status().trim());
        }
        if (request.tags() != null) {
            project.setTags(resolveTags(request.tags()));
        }

        validateProjectFields(project.getName(), project.getDescription(), project.getGithubRepo());
        project.setUpdated_at(now());

        return ProjectResponse.fromProject(projectRepository.save(project));
    }

    public ProjectResponse addStar(Long id) {
        Project project = findExistingProject(id);
        Long currentStars = project.getStars_count() == null ? 0L : project.getStars_count();
        project.setStars_count(currentStars + 1);
        project.setUpdated_at(now());

        return ProjectResponse.fromProject(projectRepository.save(project));
    }

    public ProjectResponse deleteProject(Long id) {
        Project project = findExistingProject(id);
        ProjectResponse response = ProjectResponse.fromProject(project);
        projectRepository.delete(project);
        return response;
    }

    private Project findExistingProject(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("Project id is required");
        }

        return projectRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Project not found"));
    }

    private void validateCreateRequest(ProjectRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Project data is required");
        }
        if (request.ownerId() == null) {
            throw new IllegalArgumentException("Owner id is required");
        }
        validateProjectFields(request.name(), request.description(), request.githubRepo());
    }

    private void validateProjectFields(String name, String description, String githubRepo) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Project name is required");
        }
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("Project description is required");
        }
        if (githubRepo == null || githubRepo.isBlank()) {
            throw new IllegalArgumentException("Project repository is required");
        }
    }

    private String normalizeStatus(String status) {
        return (status == null || status.isBlank()) ? "active" : status.trim();
    }

    private Set<Tag> resolveTags(Set<String> rawTags) {
        if (rawTags == null || rawTags.isEmpty()) {
            return new LinkedHashSet<>();
        }

        Set<Tag> resolvedTags = new LinkedHashSet<>();
        for (String rawTag : rawTags) {
            if (rawTag == null) {
                continue;
            }

            String normalizedTag = rawTag.trim();
            if (normalizedTag.isEmpty()) {
                continue;
            }

            Tag tag = tagRepository.findByNameIgnoreCase(normalizedTag)
                    .orElseGet(() -> {
                        Tag newTag = new Tag();
                        newTag.setName(normalizedTag);
                        return tagRepository.save(newTag);
                    });
            resolvedTags.add(tag);
        }

        return resolvedTags;
    }

    private Date now() {
        return new Date(System.currentTimeMillis());
    }
}
