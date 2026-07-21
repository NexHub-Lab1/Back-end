package com.nexhub.backend.service;

import com.nexhub.backend.model.Project;
import com.nexhub.backend.model.Task;
import com.nexhub.backend.model.TaskAssignment;
import com.nexhub.backend.model.User;
import com.nexhub.backend.repository.TaskAssignmentRepository;
import com.nexhub.backend.repository.TaskRepository;
import com.nexhub.backend.repository.UserRepository;
import com.nexhub.backend.service.github.GithubIssueClient;
import com.nexhub.backend.service.github.GithubIssueClient.GithubIssue;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class GithubIssueService {
    private static final String SYNC_PENDING = "pending";
    private static final String SYNC_SYNCED = "synced";
    private static final String SYNC_FAILED = "failed";

    private final TaskRepository taskRepository;
    private final TaskAssignmentRepository taskAssignmentRepository;
    private final UserRepository userRepository;
    private final GithubIssueClient githubIssueClient;

    @Transactional
    public Task syncTaskIssue(Long taskId) {
        Task task = taskRepository.findByIdForGithubIssueSync(taskId)
                .orElseThrow(() -> new NoSuchElementException("Task not found"));
        task.setGithubIssueSyncStatus(SYNC_PENDING);
        task.setGithubIssueLastError(null);

        try {
            syncIssue(task);
            task.setGithubIssueSyncStatus(SYNC_SYNCED);
            task.setGithubIssueLastError(null);
            task.setGithubIssueLastSyncedAt(now());
        } catch (RuntimeException e) {
            task.setGithubIssueSyncStatus(SYNC_FAILED);
            task.setGithubIssueLastError(trimError(e.getMessage()));
            task.setGithubIssueLastSyncedAt(now());
        }
        return taskRepository.save(task);
    }

    @Transactional
    public Task retryTaskIssue(Long taskId, String authenticatedEmail) {
        if (authenticatedEmail == null || authenticatedEmail.isBlank()) {
            throw new IllegalArgumentException("Authenticated owner is required");
        }
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new NoSuchElementException("Task not found"));
        User actor = userRepository.findByEmail(authenticatedEmail)
                .orElseThrow(() -> new NoSuchElementException("User not found"));
        User owner = task.getProject() != null ? task.getProject().getOwner() : null;
        if (owner == null || !Objects.equals(owner.getId(), actor.getId())) {
            throw new IllegalArgumentException("Only the project owner can retry GitHub issue synchronization");
        }
        return syncTaskIssue(taskId);
    }

    private void syncIssue(Task task) {
        Project project = task.getProject();
        if (project == null) {
            throw new IllegalArgumentException("Task project is required");
        }
        User owner = project.getOwner();
        if (owner == null || owner.getGithub_access_token() == null || owner.getGithub_access_token().isBlank()) {
            throw new IllegalArgumentException("Project owner must reconnect GitHub");
        }

        String repositoryUrl = project.getGithubRepo();
        String accessToken = owner.getGithub_access_token();
        String marker = taskMarker(task.getId());
        List<TaskAssignment> assignments = taskAssignmentRepository.findByTask_Id(task.getId()).stream()
                .filter(assignment -> !"cancelled".equalsIgnoreCase(assignment.getStatus()))
                .toList();
        String baseBody = buildBody(task, assignments, marker, List.of());

        GithubIssue issue = storedIssue(task);
        if (issue == null) {
            issue = githubIssueClient.findByTaskMarker(repositoryUrl, accessToken, marker)
                    .orElseGet(() -> githubIssueClient.createIssue(
                            repositoryUrl,
                            accessToken,
                            "[NexHub Task #" + task.getId() + "] " + task.getTitle(),
                            baseBody
                    ));
        }
        applyIssue(task, issue);

        List<String> requestedAssignees = githubUsernames(assignments);
        List<String> assignmentWarnings = new java.util.ArrayList<>();
        if (!requestedAssignees.isEmpty()) {
            try {
                Set<String> accepted = new LinkedHashSet<>(githubIssueClient.addAssignees(
                        repositoryUrl,
                        accessToken,
                        issue.number(),
                        requestedAssignees
                ).stream().map(value -> value.toLowerCase(Locale.ROOT)).toList());
                requestedAssignees.stream()
                        .filter(username -> !accepted.contains(username.toLowerCase(Locale.ROOT)))
                        .forEach(username -> assignmentWarnings.add("@" + username + " could not be assigned because GitHub does not recognize them as a repository collaborator."));
            } catch (RuntimeException e) {
                assignmentWarnings.add("GitHub could not update issue assignees: " + trimError(e.getMessage()));
            }
        }

        GithubIssue updated = githubIssueClient.updateIssueBody(
                repositoryUrl,
                accessToken,
                issue.number(),
                buildBody(task, assignments, marker, assignmentWarnings)
        );
        applyIssue(task, updated);
    }

    private GithubIssue storedIssue(Task task) {
        if (task.getGithubIssueId() == null || task.getGithubIssueNumber() == null || task.getGithubIssueUrl() == null) {
            return null;
        }
        return new GithubIssue(
                task.getGithubIssueId(),
                task.getGithubIssueNumber(),
                task.getGithubIssueUrl(),
                task.getGithubIssueState() == null ? "open" : task.getGithubIssueState()
        );
    }

    private void applyIssue(Task task, GithubIssue issue) {
        task.setGithubIssueId(issue.id());
        task.setGithubIssueNumber(issue.number());
        task.setGithubIssueUrl(issue.url());
        task.setGithubIssueState(issue.state());
    }

    private String buildBody(Task task, List<TaskAssignment> assignments, String marker, List<String> warnings) {
        StringBuilder body = new StringBuilder();
        body.append(marker).append("\n\n")
                .append("## NexHub task\n\n")
                .append("**Description**\n").append(valueOrFallback(task.getDescription())).append("\n\n")
                .append("**Deliverables**\n").append(valueOrFallback(task.getDeliverables())).append("\n\n")
                .append("**Reward**\n").append(formatReward(task.getRewardAmount(), task.getRewardCurrency())).append("\n\n")
                .append("**Deadline**\n").append(task.getDeadline() == null ? "Not specified" : task.getDeadline()).append("\n\n")
                .append("**Collaborators**\n");

        if (assignments.isEmpty()) {
            body.append("- No collaborators assigned yet\n");
        } else {
            for (TaskAssignment assignment : assignments) {
                User user = assignment.getUser();
                if (user == null) {
                    continue;
                }
                body.append("- ").append(user.getUsername());
                if (user.getGithub_username() == null || user.getGithub_username().isBlank()) {
                    body.append(" (GitHub account not connected)");
                } else {
                    body.append(" (@").append(user.getGithub_username().trim()).append(")");
                }
                body.append("\n");
            }
        }

        if (!warnings.isEmpty()) {
            body.append("\n> **GitHub assignment notes**\n");
            warnings.forEach(warning -> body.append("> - ").append(warning).append("\n"));
        }
        body.append("\n---\nThis issue is synchronized with NexHub Task #").append(task.getId()).append(".");
        return body.toString();
    }

    private List<String> githubUsernames(List<TaskAssignment> assignments) {
        return assignments.stream()
                .map(TaskAssignment::getUser)
                .filter(Objects::nonNull)
                .map(User::getGithub_username)
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
    }

    private String taskMarker(Long taskId) {
        return "<!-- nexhub-task-id:" + taskId + " -->";
    }

    private String formatReward(BigDecimal amount, String currency) {
        return (amount == null ? "0" : amount.stripTrailingZeros().toPlainString()) + " "
                + (currency == null || currency.isBlank() ? "ARS" : currency.trim().toUpperCase(Locale.ROOT));
    }

    private String valueOrFallback(String value) {
        return value == null || value.isBlank() ? "Not specified" : value.trim();
    }

    private String trimError(String message) {
        String normalized = message == null || message.isBlank() ? "GitHub issue synchronization failed" : message.trim();
        return normalized.length() <= 1000 ? normalized : normalized.substring(0, 1000);
    }

    private Timestamp now() {
        return Timestamp.from(Instant.now());
    }
}
