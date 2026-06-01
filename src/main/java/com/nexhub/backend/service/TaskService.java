package com.nexhub.backend.service;

import com.nexhub.backend.dto.PagedResponse;
import com.nexhub.backend.dto.task.TaskRequest;
import com.nexhub.backend.dto.task.TaskResponse;
import com.nexhub.backend.dto.task.TaskUpdateRequest;
import com.nexhub.backend.model.Project;
import com.nexhub.backend.model.Tag;
import com.nexhub.backend.model.Task;
import com.nexhub.backend.repository.ProjectRepository;
import com.nexhub.backend.repository.TagRepository;
import com.nexhub.backend.repository.TaskAssignmentRepository;
import com.nexhub.backend.repository.TaskRepository;
import com.nexhub.backend.repository.TaskSubmissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Date;
import java.util.LinkedHashSet;
import java.util.NoSuchElementException;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional
public class TaskService {
    private final TaskRepository taskRepository;
    private final ProjectRepository projectRepository;
    private final TagRepository tagRepository;
    private final TaskAssignmentRepository taskAssignmentRepository;
    private final TaskSubmissionRepository taskSubmissionRepository;
    private final PaymentService paymentService;

    @Transactional(readOnly = true)
    public PagedResponse<TaskResponse> getAllTasks(Pageable pageable) {
        return PagedResponse.fromPage(
                taskRepository.findAllVisible(pageable).map(TaskResponse::fromTask)
        );
    }

    @Transactional(readOnly = true)
    public TaskResponse getTaskById(Long id) {
        return TaskResponse.fromTask(findExistingTask(id));
    }

    @Transactional(readOnly = true)
    public PagedResponse<TaskResponse> getTasksByProject(Long projectId, Pageable pageable) {
        if (projectId == null) {
            throw new IllegalArgumentException("Project id is required");
        }

        return PagedResponse.fromPage(
                taskRepository.findByProjectId(projectId, pageable)
                        .map(TaskResponse::fromTask)
        );
    }

    @Transactional(readOnly = true)
    public PagedResponse<TaskResponse> getTasksByOwner(Long ownerId, Pageable pageable) {
        if (ownerId == null) {
            throw new IllegalArgumentException("Owner id is required");
        }

        return PagedResponse.fromPage(
                taskRepository.findByProjectOwnerId(ownerId, pageable)
                        .map(TaskResponse::fromTask)
        );
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

    public TaskResponse updateTask(TaskUpdateRequest request) {
        if (request == null || request.id() == null) {
            throw new IllegalArgumentException("Task id is required");
        }

        Task task = findExistingTask(request.id());
        validateFundingLockedFields(task, request);

        if (request.projectId() != null) {
            Project project = projectRepository.findById(request.projectId())
                    .orElseThrow(() -> new NoSuchElementException("Project not found"));
            task.setProject(project);
        }
        if (request.title() != null && !request.title().isBlank()) {
            task.setTitle(request.title().trim());
        }
        if (request.description() != null && !request.description().isBlank()) {
            task.setDescription(request.description().trim());
        }
        if (request.deliverables() != null) {
            task.setDeliverables(normalizeOptionalText(request.deliverables()));
        }
        if (request.rewardAmount() != null) {
            task.setRewardAmount(request.rewardAmount().stripTrailingZeros());
        }
        if (request.rewardCurrency() != null && !request.rewardCurrency().isBlank()) {
            task.setRewardCurrency(normalizeCurrency(request.rewardCurrency()));
        }
        if (request.deadline() != null) {
            task.setDeadline(request.deadline());
        }
        if (request.status() != null && !request.status().isBlank()) {
            task.setStatus(normalizeStatus(request.status()));
        }
        if (request.maxAttempts() != null) {
            task.setMaxAttempts(normalizeMaxAttempts(request.maxAttempts()));
        }
        if (request.recommendedSkills() != null) {
            task.setRecommendedSkills(resolveTags(request.recommendedSkills()));
        }

        validateTaskFields(task.getTitle(), task.getDescription(), task.getRewardAmount());
        task.setUpdated_at(now());

        return TaskResponse.fromTask(taskRepository.save(task));
    }

    public TaskResponse deleteTask(Long id) {
        Task task = findExistingTask(id);

        if (taskAssignmentRepository.existsByTask_Id(task.getId()) || taskSubmissionRepository.existsByTask_Id(task.getId())) {
            throw new IllegalArgumentException("Task has assignments or submissions and cannot be deleted. Cancel it instead.");
        }
        if (paymentService.taskHasPaymentHistory(task)) {
            throw new IllegalArgumentException("Task has payment history and cannot be deleted. Cancel it instead.");
        }

        TaskResponse response = TaskResponse.fromTask(task);
        taskRepository.delete(task);
        return response;
    }

    public TaskResponse cancelTask(Long id) {
        Task task = findExistingTask(id);
        paymentService.refundTaskEscrow(task, "Task cancelled");
        task.setStatus("cancelled");
        task.setUpdated_at(now());

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

    private void validateFundingLockedFields(Task task, TaskUpdateRequest request) {
        if (!paymentService.taskHasLockedFunding(task)) {
            return;
        }

        boolean changesProject = request.projectId() != null
                && !request.projectId().equals(task.getProject().getId());
        boolean changesRewardAmount = request.rewardAmount() != null
                && request.rewardAmount().compareTo(task.getRewardAmount()) != 0;
        boolean changesRewardCurrency = request.rewardCurrency() != null
                && !normalizeCurrency(request.rewardCurrency()).equals(task.getRewardCurrency());

        if (changesProject || changesRewardAmount || changesRewardCurrency) {
            throw new IllegalArgumentException("Task project and reward cannot be changed after funding starts");
        }
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
            return "ARS";
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
