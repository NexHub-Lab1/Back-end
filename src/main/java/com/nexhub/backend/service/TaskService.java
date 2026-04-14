package com.nexhub.backend.service;

import com.nexhub.backend.dto.task.TaskRequest;
import com.nexhub.backend.dto.task.TaskResponse;
import com.nexhub.backend.model.Project;
import com.nexhub.backend.model.Tag;
import com.nexhub.backend.model.Task;
import com.nexhub.backend.repository.ProjectRepository;
import com.nexhub.backend.repository.TagRepository;
import com.nexhub.backend.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional
public class TaskService {
    private final TaskRepository taskRepository;
    private final ProjectRepository projectRepository;
    private final TagRepository tagRepository;

    @Transactional(readOnly = true)
    public List<TaskResponse> getAllTasks() {
        return taskRepository.findAll().stream()
                .map(TaskResponse::fromTask)
                .toList();
    }

    @Transactional(readOnly = true)
    public TaskResponse getTaskById(Long id) {
        return TaskResponse.fromTask(findExistingTask(id));
    }

    @Transactional(readOnly = true)
    public List<TaskResponse> getTasksByProject(Long projectId) {
        if (projectId == null) {
            throw new IllegalArgumentException("Project id is required");
        }

        return taskRepository.findByProjectIdOrderByCreated_atDesc(projectId).stream()
                .map(TaskResponse::fromTask)
                .toList();
    }

    public TaskResponse createTask(TaskRequest request) {
        validateCreateRequest(request);

        Project project = projectRepository.findById(request.projectId())
                .orElseThrow(() -> new NoSuchElementException("Project not found"));

        Task task = new Task();
        task.setProject(project);
        task.setTitle(request.title().trim());
        task.setDescription(request.description().trim());
        task.setDeliverables(normalizeOptionalText(request.deliverables()));
        task.setRewardAmount(request.rewardAmount().stripTrailingZeros());
        task.setRewardCurrency(normalizeCurrency(request.rewardCurrency()));
        task.setDeadline(request.deadline());
        task.setStatus(normalizeStatus(request.status()));
        task.setMaxAttempts(normalizeMaxAttempts(request.maxAttempts()));
        task.setCreated_at(now());
        task.setUpdated_at(now());
        task.setRecommendedSkills(resolveTags(request.recommendedSkills()));

        return TaskResponse.fromTask(taskRepository.save(task));
    }

    private Task findExistingTask(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("Task id is required");
        }

        return taskRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Task not found"));
    }

    private void validateCreateRequest(TaskRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Task data is required");
        }
        if (request.projectId() == null) {
            throw new IllegalArgumentException("Project id is required");
        }
        validateTaskFields(request.title(), request.description(), request.rewardAmount());
    }

    private void validateTaskFields(String title, String description, BigDecimal rewardAmount) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Task title is required");
        }
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("Task description is required");
        }
        if (rewardAmount == null || rewardAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Task reward amount must be greater than zero");
        }
    }

    private String normalizeOptionalText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String normalizeCurrency(String rewardCurrency) {
        if (rewardCurrency == null || rewardCurrency.isBlank()) {
            return "USD";
        }
        return rewardCurrency.trim().toUpperCase();
    }

    private String normalizeStatus(String status) {
        return (status == null || status.isBlank()) ? "open" : status.trim().toLowerCase();
    }

    private Integer normalizeMaxAttempts(Integer maxAttempts) {
        if (maxAttempts == null || maxAttempts < 1) {
            return 1;
        }
        return maxAttempts;
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
