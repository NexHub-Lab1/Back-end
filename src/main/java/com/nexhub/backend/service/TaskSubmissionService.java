package com.nexhub.backend.service;

import com.nexhub.backend.dto.PagedResponse;
import com.nexhub.backend.dto.tasksubmission.TaskSubmissionRequest;
import com.nexhub.backend.dto.tasksubmission.TaskSubmissionResponse;
import com.nexhub.backend.dto.tasksubmission.TaskSubmissionUpdateRequest;
import com.nexhub.backend.model.Project;
import com.nexhub.backend.model.Task;
import com.nexhub.backend.model.TaskAssignment;
import com.nexhub.backend.model.TaskSubmission;
import com.nexhub.backend.model.User;
import com.nexhub.backend.repository.TaskAssignmentRepository;
import com.nexhub.backend.repository.TaskSubmissionRepository;
import com.nexhub.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.URISyntaxException;
import java.sql.Date;
import java.util.HashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional
public class TaskSubmissionService {
    private static final String ACTIVE_ASSIGNMENT_STATUS = "active";
    private static final String COMPLETED_ASSIGNMENT_STATUS = "completed";
    private static final String SUBMITTED_STATUS = "submitted";
    private static final String APPROVED_STATUS = "approved";
    private static final String REJECTED_STATUS = "rejected";
    private static final String CHANGES_REQUESTED_STATUS = "changes_requested";
    private static final String NOT_SELECTED_STATUS = "not_selected";
    private static final String CANCELLED_ASSIGNMENT_STATUS = "cancelled";
    private static final Set<String> COMPETING_PENDING_STATUSES = Set.of(
            SUBMITTED_STATUS,
            CHANGES_REQUESTED_STATUS
    );
    private static final Set<String> ALLOWED_STATUSES = Set.of(
            SUBMITTED_STATUS,
            APPROVED_STATUS,
            REJECTED_STATUS,
            CHANGES_REQUESTED_STATUS
    );

    private final TaskSubmissionRepository taskSubmissionRepository;
    private final TaskAssignmentRepository taskAssignmentRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final PaymentService paymentService;

    @Transactional(readOnly = true)
    public PagedResponse<TaskSubmissionResponse> getAllSubmissions(Pageable pageable) {
        return PagedResponse.fromPage(
                taskSubmissionRepository.findAll(pageable).map(TaskSubmissionResponse::fromTaskSubmission)
        );
    }

    @Transactional(readOnly = true)
    public TaskSubmissionResponse getSubmissionById(Long id) {
        return TaskSubmissionResponse.fromTaskSubmission(findExistingSubmission(id));
    }

    @Transactional(readOnly = true)
    public PagedResponse<TaskSubmissionResponse> getSubmissionsByTask(Long taskId, Pageable pageable) {
        if (taskId == null) {
            throw new IllegalArgumentException("Task id is required");
        }

        return PagedResponse.fromPage(
                taskSubmissionRepository.findByTaskId(taskId, pageable)
                        .map(TaskSubmissionResponse::fromTaskSubmission)
        );
    }

    @Transactional(readOnly = true)
    public PagedResponse<TaskSubmissionResponse> getSubmissionsByAssignment(Long assignmentId, Pageable pageable) {
        if (assignmentId == null) {
            throw new IllegalArgumentException("Assignment id is required");
        }

        return PagedResponse.fromPage(
                taskSubmissionRepository.findByAssignmentId(assignmentId, pageable)
                        .map(TaskSubmissionResponse::fromTaskSubmission)
        );
    }

    @Transactional(readOnly = true)
    public PagedResponse<TaskSubmissionResponse> getSubmissionsByUser(Long userId, Pageable pageable) {
        if (userId == null) {
            throw new IllegalArgumentException("User id is required");
        }

        return PagedResponse.fromPage(
                taskSubmissionRepository.findByUserId(userId, pageable)
                        .map(TaskSubmissionResponse::fromTaskSubmission)
        );
    }

    @Transactional(readOnly = true)
    public PagedResponse<TaskSubmissionResponse> getSubmissionsToReview(Long reviewerId, String status, Pageable pageable) {
        if (reviewerId == null) {
            throw new IllegalArgumentException("Reviewer id is required");
        }

        if (status != null && !status.isBlank()) {
            return PagedResponse.fromPage(
                    taskSubmissionRepository.findByProjectOwnerIdAndStatus(reviewerId, status.trim(), pageable)
                            .map(TaskSubmissionResponse::fromTaskSubmission)
            );
        }

        return PagedResponse.fromPage(
                taskSubmissionRepository.findByProjectOwnerId(reviewerId, pageable)
                        .map(TaskSubmissionResponse::fromTaskSubmission)
        );
    }

    public TaskSubmissionResponse createSubmission(TaskSubmissionRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Submission data is required");
        }
        if (request.assignmentId() == null) {
            throw new IllegalArgumentException("Assignment id is required");
        }

        TaskAssignment assignment = taskAssignmentRepository.findById(request.assignmentId())
                .orElseThrow(() -> new NoSuchElementException("Assignment not found"));

        validateAssignmentCanReceiveSubmission(assignment);

        int nextAttempt = currentAttemptsUsed(assignment) + 1;
        Task task = assignment.getTask();

        if ("DESIGN".equalsIgnoreCase(task.getTaskType()) || "VISUAL".equalsIgnoreCase(task.getTaskType())) {
            validateDesignUrl(request.designUrl());
        } else {
            validatePullRequestUrl(request.pullRequestUrl());
        }

        TaskSubmission submission = new TaskSubmission();
        submission.setAssignment(assignment);
        submission.setTask(task);
        submission.setUser(assignment.getUser());
        submission.setPullRequestUrl(request.pullRequestUrl() != null ? request.pullRequestUrl().trim() : null);
        submission.setDesignUrl(request.designUrl() != null ? request.designUrl().trim() : null);
        submission.setDescription(request.description());
        submission.setDemoUrl(request.demoUrl());
        submission.setSubmittedAt(now());
        submission.setStatus(SUBMITTED_STATUS);
        submission.setAttemptsUsed(nextAttempt);

        assignment.setAttemptsUsed(nextAttempt);
        taskAssignmentRepository.save(assignment);

        TaskSubmission savedSubmission = taskSubmissionRepository.save(submission);

        User projectOwner = task.getProject().getOwner();
        notificationService.sendNotification(
                projectOwner,
                "New submission from " + submission.getUser().getUsername() + " for task: " + task.getTitle(),
                "INFO",
                "/task/" + task.getId()
        );

        return TaskSubmissionResponse.fromTaskSubmission(savedSubmission);
    }

    public TaskSubmissionResponse updateSubmission(TaskSubmissionUpdateRequest request, String authenticatedEmail) {
        if (request == null || request.id() == null) {
            throw new IllegalArgumentException("Submission id is required");
        }

        TaskSubmission submission = findExistingSubmission(request.id());
        String previousStatus = submission.getStatus();

        if (request.pullRequestUrl() != null && ("DEVELOPMENT".equalsIgnoreCase(submission.getTask().getTaskType()) || submission.getTask().getTaskType() == null)) {
            validatePullRequestUrl(request.pullRequestUrl());
            submission.setPullRequestUrl(request.pullRequestUrl().trim());
        }

        if (request.designUrl() != null && ("DESIGN".equalsIgnoreCase(submission.getTask().getTaskType()) || "VISUAL".equalsIgnoreCase(submission.getTask().getTaskType()))) {
            validateDesignUrl(request.designUrl());
            submission.setDesignUrl(request.designUrl().trim());
        }

        if (request.reviewComments() != null) {
            submission.setReviewComments(normalizeOptionalText(request.reviewComments()));
        }

        if (request.status() != null && !request.status().isBlank()) {
            String status = normalizeStatus(request.status());
            validateApprovedSubmissionCannotBeReopened(previousStatus, status);
            submission.setStatus(status);

            if (SUBMITTED_STATUS.equals(status)) {
                submission.setReviewer(null);
                submission.setReviewedAt(null);
                syncAssignmentStatusAfterStatusChange(submission, previousStatus, status);
            } else if (isReviewStatus(status)) {
                User reviewer = findAuthenticatedReviewer(authenticatedEmail);
                validateReviewerOwnsProject(reviewer, submission);
                submission.setReviewer(reviewer);
                submission.setReviewedAt(now());
                syncAssignmentStatusAfterStatusChange(submission, previousStatus, status);
                if (APPROVED_STATUS.equals(status) && !APPROVED_STATUS.equalsIgnoreCase(previousStatus)) {
                    paymentService.releaseRewardForApprovedSubmission(submission);
                    closeCompetingSubmissions(submission, reviewer);
                }

                User developer = submission.getUser();
                if (APPROVED_STATUS.equals(status)) {
                    int currentPoints = developer.getTotal_points() != null ? developer.getTotal_points() : 0;
                    if (submission.getTask().getRewardAmount() != null) {
                        developer.setTotal_points(currentPoints + submission.getTask().getRewardAmount().intValue());
                    }
                    int currentRep = developer.getReputation_score() != null ? developer.getReputation_score() : 0;
                    developer.setReputation_score(currentRep + 15);
                    int currentStreak = developer.getStreak_day() != null ? developer.getStreak_day() : 0;
                    developer.setStreak_day(currentStreak + 1);
                } else if (REJECTED_STATUS.equals(status)) {
                    int currentRep = developer.getReputation_score() != null ? developer.getReputation_score() : 0;
                    int penalty = 10;
                    if ("SPAM_OR_LOW_EFFORT".equalsIgnoreCase(request.rejectionReason())) {
                        penalty = 25;
                        developer.setStreak_day(0);
                    }
                    developer.setReputation_score(currentRep - penalty);
                }
                userRepository.save(developer);

                String type = "INFO";
                String message = "Your submission for '" + submission.getTask().getTitle() + "' has been " + status;
                if (APPROVED_STATUS.equals(status)) type = "SUCCESS";
                if (REJECTED_STATUS.equals(status)) type = "WARNING";

                notificationService.sendNotification(
                        submission.getUser(),
                        message,
                        type,
                        "/task/" + submission.getTask().getId()
                );
            }
        }

        return TaskSubmissionResponse.fromTaskSubmission(taskSubmissionRepository.save(submission));
    }

    private void closeCompetingSubmissions(TaskSubmission approvedSubmission, User reviewer) {
        Task task = approvedSubmission.getTask();
        if (task == null || task.getId() == null || approvedSubmission.getId() == null) {
            return;
        }

        List<TaskSubmission> competingSubmissions = taskSubmissionRepository
                .findByTask_IdAndIdNotAndStatusIn(
                        task.getId(),
                        approvedSubmission.getId(),
                        COMPETING_PENDING_STATUSES
                );
        if (competingSubmissions.isEmpty()) {
            return;
        }

        Date reviewedAt = now();
        Set<Long> notifiedUserIds = new HashSet<>();
        Set<Long> closedAssignmentIds = new HashSet<>();
        Long approvedAssignmentId = approvedSubmission.getAssignment() == null
                ? null
                : approvedSubmission.getAssignment().getId();

        for (TaskSubmission competingSubmission : competingSubmissions) {
            competingSubmission.setStatus(NOT_SELECTED_STATUS);
            competingSubmission.setReviewer(reviewer);
            competingSubmission.setReviewedAt(reviewedAt);
            competingSubmission.setReviewComments(selectionClosedMessage(competingSubmission.getReviewComments()));

            TaskAssignment assignment = competingSubmission.getAssignment();
            if (assignment != null
                    && assignment.getId() != null
                    && !assignment.getId().equals(approvedAssignmentId)
                    && closedAssignmentIds.add(assignment.getId())) {
                assignment.setStatus(CANCELLED_ASSIGNMENT_STATUS);
                taskAssignmentRepository.save(assignment);
            }

            User developer = competingSubmission.getUser();
            if (developer != null && developer.getId() != null && notifiedUserIds.add(developer.getId())) {
                notificationService.sendNotification(
                        developer,
                        "Another submission was selected for '" + task.getTitle()
                                + "'. Your submission was closed without a reputation penalty.",
                        "INFO",
                        "/task/" + task.getId()
                );
            }
        }

        taskSubmissionRepository.saveAll(competingSubmissions);
    }

    private String selectionClosedMessage(String currentComments) {
        String message = "Another submission was approved for this task. No reputation penalty was applied.";
        String normalizedComments = normalizeOptionalText(currentComments);
        return normalizedComments == null ? message : normalizedComments + "\n\n" + message;
    }

    public TaskSubmissionResponse deleteSubmission(Long id) {
        TaskSubmission submission = findExistingSubmission(id);
        TaskSubmissionResponse response = TaskSubmissionResponse.fromTaskSubmission(submission);
        taskSubmissionRepository.delete(submission);
        return response;
    }

    private TaskSubmission findExistingSubmission(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("Submission id is required");
        }

        return taskSubmissionRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Submission not found"));
    }



    private void validateAssignmentCanReceiveSubmission(TaskAssignment assignment) {
        if (assignment.getTask() == null) {
            throw new IllegalArgumentException("Assignment task is required");
        }
        if (assignment.getUser() == null) {
            throw new IllegalArgumentException("Assignment user is required");
        }
        if (!ACTIVE_ASSIGNMENT_STATUS.equalsIgnoreCase(assignment.getStatus())) {
            throw new IllegalArgumentException("Assignment must be active to submit work");
        }
        paymentService.validateTaskCanReceiveWork(assignment.getTask());

        Task task = assignment.getTask();
        if (task.getDeadline() != null && task.getDeadline().before(now())) {
            throw new IllegalArgumentException("Cannot submit deliverable: the task deadline has already passed");
        }

        int maxAttempts = maxAttemptsForTask(assignment.getTask());
        if (currentAttemptsUsed(assignment) >= maxAttempts) {
            throw new IllegalArgumentException("Assignment has reached the maximum number of attempts");
        }
    }

    private void validatePullRequestUrl(String pullRequestUrl) {
        if (pullRequestUrl == null || pullRequestUrl.isBlank()) {
            throw new IllegalArgumentException("Pull request URL is required for development tasks");
        }

        try {
            URI uri = new URI(pullRequestUrl.trim());
            String scheme = uri.getScheme();
            if (scheme == null
                    || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))
                    || uri.getHost() == null
                    || uri.getHost().isBlank()) {
                throw new IllegalArgumentException("Pull request URL must be a valid URL");
            }
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Pull request URL must be a valid URL");
        }
    }

    private void validateDesignUrl(String designUrl) {
        if (designUrl == null || designUrl.isBlank()) {
            throw new IllegalArgumentException("Design URL is required for visual tasks");
        }

        try {
            URI uri = new URI(designUrl.trim());
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (scheme == null
                    || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))
                    || host == null
                    || host.isBlank()) {
                throw new IllegalArgumentException("Design URL must be a valid URL");
            }
            if (!host.toLowerCase().contains("figma.com")) {
                throw new IllegalArgumentException("Design URL must be a valid Figma link (figma.com)");
            }
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Design URL must be a valid URL");
        }
    }

    private User findAuthenticatedReviewer(String authenticatedEmail) {
        if (authenticatedEmail == null || authenticatedEmail.isBlank()) {
            throw new IllegalArgumentException("Authenticated reviewer is required");
        }

        return userRepository.findByEmail(authenticatedEmail)
                .orElseThrow(() -> new NoSuchElementException("Reviewer not found"));
    }

    private void validateReviewerOwnsProject(User reviewer, TaskSubmission submission) {
        Project project = submission.getTask() != null ? submission.getTask().getProject() : null;
        User owner = project != null ? project.getOwner() : null;

        if (owner == null || owner.getId() == null || !owner.getId().equals(reviewer.getId())) {
            throw new IllegalArgumentException("Only the project owner can review this submission");
        }
    }

    private void syncAssignmentStatusAfterStatusChange(TaskSubmission submission, String previousStatus, String status) {
        TaskAssignment assignment = submission.getAssignment();
        if (assignment == null) {
            return;
        }

        if (APPROVED_STATUS.equals(status)) {
            assignment.setStatus(COMPLETED_ASSIGNMENT_STATUS);
            taskAssignmentRepository.save(assignment);
            return;
        }

        if (REJECTED_STATUS.equals(status)) {
            int maxAttempts = maxAttemptsForTask(submission.getTask());
            if (submission.getAttemptsUsed() != null && submission.getAttemptsUsed() >= maxAttempts) {
                assignment.setStatus("failed");
            } else {
                assignment.setStatus(ACTIVE_ASSIGNMENT_STATUS);
            }
            taskAssignmentRepository.save(assignment);
            return;
        }

        if (APPROVED_STATUS.equalsIgnoreCase(previousStatus)) {
            assignment.setStatus(ACTIVE_ASSIGNMENT_STATUS);
            taskAssignmentRepository.save(assignment);
        }
    }

    private boolean isReviewStatus(String status) {
        return APPROVED_STATUS.equals(status)
                || REJECTED_STATUS.equals(status)
                || CHANGES_REQUESTED_STATUS.equals(status);
    }

    private void validateApprovedSubmissionCannotBeReopened(String previousStatus, String status) {
        if (APPROVED_STATUS.equalsIgnoreCase(previousStatus) && !APPROVED_STATUS.equals(status)) {
            throw new IllegalArgumentException("Approved submissions cannot be changed because the reward has already been released");
        }
    }

    private String normalizeStatus(String status) {
        String normalizedStatus = status.trim().toLowerCase();
        if (!ALLOWED_STATUSES.contains(normalizedStatus)) {
            throw new IllegalArgumentException("Submission status is invalid");
        }
        return normalizedStatus;
    }

    private String normalizeOptionalText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private int currentAttemptsUsed(TaskAssignment assignment) {
        Integer attemptsUsed = assignment.getAttemptsUsed();
        if (attemptsUsed == null || attemptsUsed < 0) {
            return 0;
        }
        return attemptsUsed;
    }

    private int maxAttemptsForTask(Task task) {
        Integer maxAttempts = task.getMaxAttempts();
        if (maxAttempts == null || maxAttempts < 1) {
            return 1;
        }
        return maxAttempts;
    }

    private Date now() {
        return new Date(System.currentTimeMillis());
    }
}
