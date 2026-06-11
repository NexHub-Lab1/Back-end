package com.nexhub.backend.service;

import com.nexhub.backend.dto.PagedResponse;
import com.nexhub.backend.dto.taskassignment.TaskAssignmentRequest;
import com.nexhub.backend.dto.taskassignment.TaskAssignmentResponse;
import com.nexhub.backend.dto.taskassignment.TaskAssignmentUpdateRequest;
import com.nexhub.backend.model.Project;
import com.nexhub.backend.model.Task;
import com.nexhub.backend.model.TaskAssignment;
import com.nexhub.backend.model.User;
import com.nexhub.backend.repository.TaskAssignmentRepository;
import com.nexhub.backend.repository.TaskRepository;
import com.nexhub.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
@Transactional
public class TaskAssignmentService {
    private static final String ACTIVE_STATUS = "active";

    private final TaskAssignmentRepository taskAssignmentRepository;
    private final TaskRepository taskRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    @Transactional(readOnly = true)
    public PagedResponse<TaskAssignmentResponse> getAllAssignments(Pageable pageable) {
        return PagedResponse.fromPage(
                taskAssignmentRepository.findAll(pageable).map(TaskAssignmentResponse::fromTaskAssignment)
        );
    }

    @Transactional(readOnly = true)
    public TaskAssignmentResponse getAssignmentById(Long id) {
        return TaskAssignmentResponse.fromTaskAssignment(findExistingAssignment(id));
    }

    @Transactional(readOnly = true)
    public PagedResponse<TaskAssignmentResponse> getAssignmentsByTask(Long taskId, Pageable pageable) {
        if (taskId == null) {
            throw new IllegalArgumentException("Task id is required");
        }

        return PagedResponse.fromPage(
                taskAssignmentRepository.findByTaskId(taskId, pageable)
                        .map(TaskAssignmentResponse::fromTaskAssignment)
        );
    }

    @Transactional(readOnly = true)
    public PagedResponse<TaskAssignmentResponse> getAssignmentsByUser(Long userId, Pageable pageable, boolean openOnly) {
        if (userId == null) {
            throw new IllegalArgumentException("User id is required");
        }

        if (openOnly) {
            return PagedResponse.fromPage(
                    taskAssignmentRepository.findOpenByUserId(userId, pageable)
                            .map(TaskAssignmentResponse::fromTaskAssignment)
            );
        }

        return PagedResponse.fromPage(
                taskAssignmentRepository.findByUserId(userId, pageable)
                        .map(TaskAssignmentResponse::fromTaskAssignment)
        );
    }

    public TaskAssignmentResponse createAssignment(TaskAssignmentRequest request) {
        validateCreateRequest(request);

        Task task = taskRepository.findById(request.taskId())
                .orElseThrow(() -> new NoSuchElementException("Task not found"));
        User user = userRepository.findById(request.userId())
                .orElseThrow(() -> new NoSuchElementException("User not found"));

        validateUserCanTakeTask(task, user);
        validateTaskHasNoActiveAssignment(task.getId(), null);

        TaskAssignment assignment = new TaskAssignment();
        assignment.setTask(task);
        assignment.setUser(user);
        assignment.setAssignedAt(now());
        assignment.setStatus(ACTIVE_STATUS);
        assignment.setAttemptsUsed(0);

        TaskAssignment savedAssignment = taskAssignmentRepository.save(assignment);

        notificationService.sendNotification(
                user,
                "You have been assigned to the task: " + task.getTitle(),
                "INFO",
                "/task/" + task.getId()
        );

        return TaskAssignmentResponse.fromTaskAssignment(savedAssignment);
    }

    public TaskAssignmentResponse updateAssignment(TaskAssignmentUpdateRequest request) {
        if (request == null || request.id() == null) {
            throw new IllegalArgumentException("Assignment id is required");
        }

        TaskAssignment assignment = findExistingAssignment(request.id());

        if (request.status() != null && !request.status().isBlank()) {
            String status = normalizeStatus(request.status());
            if (ACTIVE_STATUS.equals(status)) {
                validateTaskHasNoActiveAssignment(assignment.getTask().getId(), assignment.getId());
            }
            assignment.setStatus(status);
        }

        if (request.attemptsUsed() != null) {
            assignment.setAttemptsUsed(normalizeAttemptsUsed(request.attemptsUsed()));
        }

        return TaskAssignmentResponse.fromTaskAssignment(taskAssignmentRepository.save(assignment));
    }

    public TaskAssignmentResponse deleteAssignment(Long id) {
        TaskAssignment assignment = findExistingAssignment(id);
        TaskAssignmentResponse response = TaskAssignmentResponse.fromTaskAssignment(assignment);
        taskAssignmentRepository.delete(assignment);
        return response;
    }

    private TaskAssignment findExistingAssignment(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("Assignment id is required");
        }

        return taskAssignmentRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Assignment not found"));
    }

    private void validateCreateRequest(TaskAssignmentRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Assignment data is required");
        }
        if (request.taskId() == null) {
            throw new IllegalArgumentException("Task id is required");
        }
        if (request.userId() == null) {
            throw new IllegalArgumentException("User id is required");
        }
    }

    private void validateUserCanTakeTask(Task task, User user) {
        Project project = task.getProject();
        User owner = project != null ? project.getOwner() : null;

        if (owner != null && owner.getId() != null && owner.getId().equals(user.getId())) {
            throw new IllegalArgumentException("Project owner cannot be assigned to their own task");
        }

        if (task.getDeadline() != null && task.getDeadline().before(now())) {
            throw new IllegalArgumentException("Cannot claim task: the deadline has already passed");
        }

        int minRep = task.getMinReputation() != null ? task.getMinReputation() : 0;
        int userRep = user.getReputation_score() != null ? user.getReputation_score() : 0;
        if (userRep < minRep) {
            throw new IllegalArgumentException("Your reputation score is too low to claim this task (Minimum required: " + minRep + " Reputation)");
        }
    }

    private void validateTaskHasNoActiveAssignment(Long taskId, Long assignmentIdToIgnore) {
        java.util.List<TaskAssignment> activeAssignments = taskAssignmentRepository.findByTask_Id(taskId).stream()
                .filter(a -> ACTIVE_STATUS.equalsIgnoreCase(a.getStatus()))
                .collect(java.util.stream.Collectors.toList());

        if (activeAssignments.isEmpty()) {
            return;
        }

        if (assignmentIdToIgnore == null) {
            throw new IllegalArgumentException("Task already has an active assignment");
        }

        TaskAssignment targetAssignment = taskAssignmentRepository.findById(assignmentIdToIgnore)
                .orElse(null);
        if (targetAssignment == null) {
            throw new IllegalArgumentException("Assignment to update not found");
        }

        Long parentId = targetAssignment.getParentAssignment() != null
                ? targetAssignment.getParentAssignment().getId()
                : targetAssignment.getId();

        boolean hasOutsideActive = activeAssignments.stream()
                .anyMatch(a -> {
                    if (a.getId().equals(assignmentIdToIgnore)) {
                        return false;
                    }
                    Long otherParentId = a.getParentAssignment() != null
                            ? a.getParentAssignment().getId()
                            : a.getId();
                    return !otherParentId.equals(parentId);
                });

        if (hasOutsideActive) {
            throw new IllegalArgumentException("Task already has an active assignment from another developer or team");
        }
    }

    private String normalizeStatus(String status) {
        return status.trim().toLowerCase();
    }

    private Integer normalizeAttemptsUsed(Integer attemptsUsed) {
        if (attemptsUsed < 0) {
            throw new IllegalArgumentException("Attempts used cannot be negative");
        }
        return attemptsUsed;
    }

    private Date now() {
        return new Date(System.currentTimeMillis());
    }
}
