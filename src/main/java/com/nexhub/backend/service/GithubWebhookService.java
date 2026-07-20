package com.nexhub.backend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexhub.backend.model.Project;
import com.nexhub.backend.model.GithubWebhookDelivery;
import com.nexhub.backend.model.Task;
import com.nexhub.backend.model.TaskSubmission;
import com.nexhub.backend.model.User;
import com.nexhub.backend.repository.GithubWebhookDeliveryRepository;
import com.nexhub.backend.repository.ProjectRepository;
import com.nexhub.backend.repository.TaskAssignmentRepository;
import com.nexhub.backend.repository.TaskRepository;
import com.nexhub.backend.repository.TaskSubmissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class GithubWebhookService {
    private static final String GITHUB_API_URL = "https://api.github.com";
    private static final String GITHUB_USER_AGENT = "NexHub";
    private static final String WEBHOOK_CONNECTED = "connected";
    private static final String WEBHOOK_FAILED = "failed";
    private static final String WEBHOOK_PENDING = "pending";

    private final ProjectRepository projectRepository;
    private final TaskSubmissionRepository taskSubmissionRepository;
    private final TaskRepository taskRepository;
    private final TaskAssignmentRepository taskAssignmentRepository;
    private final NotificationService notificationService;
    private final GithubActivityService githubActivityService;
    private final GithubWebhookDeliveryRepository githubWebhookDeliveryRepository;
    private final GithubWebhookVerifier githubWebhookVerifier;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Value("${github.webhook-url:}")
    private String webhookUrl;

    @Value("${github.webhook-secret:}")
    private String webhookSecret;

    @Transactional
    public Project ensureProjectWebhook(Project project) {
        if (project == null) {
            return null;
        }

        project.setGithubWebhookStatus(WEBHOOK_PENDING);
        project.setGithubWebhookLastError(null);

        if (webhookUrl == null || webhookUrl.isBlank()) {
            return markWebhookFailed(project, "GitHub webhook URL is not configured");
        }
        if (webhookSecret == null || webhookSecret.isBlank()) {
            return markWebhookFailed(project, "GitHub webhook secret is not configured");
        }
        if (project.getOwner() == null || project.getOwner().getGithub_access_token() == null
                || project.getOwner().getGithub_access_token().isBlank()) {
            return markWebhookFailed(project, "Project owner must reconnect GitHub with webhook permissions");
        }

        try {
            RepoSlug repoSlug = parseRepoSlug(project.getGithubRepo());
            String accessToken = project.getOwner().getGithub_access_token();
            Long hookId = findExistingHookId(repoSlug, accessToken)
                    .map(existingHookId -> updateHook(repoSlug, existingHookId, accessToken))
                    .orElseGet(() -> createHook(repoSlug, accessToken));

            project.setGithubWebhookId(hookId);
            project.setGithubWebhookStatus(WEBHOOK_CONNECTED);
            project.setGithubWebhookLastError(null);
            project.setGithubWebhookConnectedAt(now());
            return projectRepository.save(project);
        } catch (IllegalArgumentException e) {
            return markWebhookFailed(project, e.getMessage());
        }
    }

    @Transactional
    public void processWebhook(String event, String deliveryId, String signatureHeader, String payload) {
        githubWebhookVerifier.validate(payload, signatureHeader);

        if (event == null || event.isBlank()) {
            throw new IllegalArgumentException("GitHub event is required");
        }
        if (deliveryId != null && !deliveryId.isBlank()
                && githubWebhookDeliveryRepository.existsById(deliveryId)) {
            return;
        }

        JsonNode root = readPayload(payload);
        switch (event) {
            case "ping" -> handlePing(root);
            case "pull_request" -> handlePullRequest(root);
            case "pull_request_review" -> handlePullRequestReview(root, deliveryId);
            case "issue_comment" -> handlePullRequestComment(root, deliveryId, "issue_comment");
            case "pull_request_review_comment" -> handlePullRequestComment(root, deliveryId, "pull_request_review_comment");
            case "issues" -> handleIssue(root, deliveryId);
            default -> {
                // Valid but unsupported GitHub events are intentionally acknowledged.
            }
        }
        recordDelivery(event, deliveryId);
    }

    private void handleIssue(JsonNode root, String deliveryId) {
        JsonNode issue = root.path("issue");
        if (issue.isMissingNode() || issue.isNull() || issue.has("pull_request")) {
            return;
        }

        Long issueId = longAt(root, "/issue/id");
        if (issueId == null) {
            return;
        }
        Optional<Task> storedTask = taskRepository.findByGithubIssueId(issueId);
        if (storedTask.isEmpty()) {
            return;
        }

        Task task = storedTask.get();
        String repoUrl = textAt(root, "/repository/html_url");
        if (!sameRepository(task, repoUrl)) {
            return;
        }

        if (deliveryId != null && !deliveryId.isBlank()
                && deliveryId.equals(task.getGithubIssueLastDeliveryId())) {
            return;
        }

        List<Project> projects = findProjectsByRepoUrl(repoUrl);
        touchWebhookDelivery(projects);
        String action = text(root.path("action"));
        String state = text(issue.path("state"));
        String stateReason = text(issue.path("state_reason"));

        if ("closed".equals(action)) {
            task.setGithubIssueState("closed");
            if ("completed".equals(stateReason)) {
                if (!"cancelled".equalsIgnoreCase(task.getStatus())) {
                    task.setStatus("completed");
                }
                notifyIssueUsers(task, "GitHub Issue #" + task.getGithubIssueNumber()
                        + " for '" + task.getTitle() + "' was completed.", "SUCCESS");
            } else if ("not_planned".equals(stateReason)) {
                notifyOwner(task, "GitHub Issue #" + task.getGithubIssueNumber()
                        + " for '" + task.getTitle() + "' was closed as not planned. Funding was not changed.", "WARNING");
            }
        } else if ("reopened".equals(action)) {
            task.setGithubIssueState("open");
            boolean protectedStatus = "cancelled".equalsIgnoreCase(task.getStatus())
                    || "released".equalsIgnoreCase(task.getFundingStatus());
            if (!protectedStatus) {
                task.setStatus("in_progress");
            }
            notifyIssueUsers(task, "GitHub Issue #" + task.getGithubIssueNumber()
                    + " for '" + task.getTitle() + "' was reopened.", "INFO");
        } else {
            return;
        }

        if (state != null && !state.isBlank()) {
            task.setGithubIssueState(state.toLowerCase(Locale.ROOT));
        }
        task.setGithubIssueLastDeliveryId(deliveryId == null || deliveryId.isBlank() ? null : deliveryId);
        task.setGithubIssueLastSyncedAt(Timestamp.from(Instant.now()));
        task.setGithubIssueSyncStatus("synced");
        task.setGithubIssueLastError(null);
        taskRepository.save(task);
    }

    private boolean sameRepository(Task task, String repositoryUrl) {
        Project project = task.getProject();
        String taskRepo = project == null ? null : normalizeUrl(project.getGithubRepo());
        String eventRepo = normalizeUrl(repositoryUrl);
        return taskRepo != null && taskRepo.equals(eventRepo);
    }

    private void notifyIssueUsers(Task task, String message, String type) {
        Set<Long> notifiedUserIds = new LinkedHashSet<>();
        User owner = task.getProject() == null ? null : task.getProject().getOwner();
        notifyOnce(owner, message, type, task, notifiedUserIds);
        for (var assignment : taskAssignmentRepository.findByTask_Id(task.getId())) {
            notifyOnce(assignment.getUser(), message, type, task, notifiedUserIds);
        }
    }

    private void notifyOwner(Task task, String message, String type) {
        User owner = task.getProject() == null ? null : task.getProject().getOwner();
        sendNotification(owner, message, type, "/task/" + task.getId());
    }

    private void notifyOnce(User user, String message, String type, Task task, Set<Long> notifiedUserIds) {
        if (user == null || user.getId() == null || !notifiedUserIds.add(user.getId())) {
            return;
        }
        sendNotification(user, message, type, "/task/" + task.getId());
    }

    private void handlePing(JsonNode root) {
        String repoUrl = textAt(root, "/repository/html_url");
        List<Project> projects = findProjectsByRepoUrl(repoUrl);
        Long hookId = longAt(root, "/hook/id");
        for (Project project : projects) {
            if (hookId != null) {
                project.setGithubWebhookId(hookId);
            }
            project.setGithubWebhookStatus(WEBHOOK_CONNECTED);
            project.setGithubWebhookLastError(null);
            project.setGithubWebhookConnectedAt(project.getGithubWebhookConnectedAt() == null ? now() : project.getGithubWebhookConnectedAt());
            project.setGithubWebhookLastDeliveryAt(now());
            projectRepository.save(project);
        }
    }

    private void handlePullRequest(JsonNode root) {
        String action = text(root.path("action"));
        JsonNode pullRequest = root.path("pull_request");
        String repoUrl = textAt(root, "/repository/html_url");
        String pullRequestUrl = text(pullRequest.path("html_url"));
        int number = pullRequest.path("number").asInt();
        boolean merged = pullRequest.path("merged").asBoolean(false);
        String title = text(pullRequest.path("title"));

        List<Project> projects = findProjectsByRepoUrl(repoUrl);
        touchWebhookDelivery(projects);

        Optional<TaskSubmission> submission = findSubmissionByPullRequestUrl(pullRequestUrl);
        if (submission.isPresent()) {
            notifySubmissionPullRequest(submission.get(), action, number, title, merged);
            return;
        }

        notifyProjectOwners(projects, buildProjectPullRequestMessage(action, number, title, merged), "INFO");
    }

    private void handlePullRequestReview(JsonNode root, String deliveryId) {
        JsonNode pullRequest = root.path("pull_request");
        JsonNode review = root.path("review");
        String action = text(root.path("action"));
        String repoUrl = textAt(root, "/repository/html_url");
        String pullRequestUrl = text(pullRequest.path("html_url"));
        int number = pullRequest.path("number").asInt();
        String state = "dismissed".equals(action) ? "dismissed" : text(review.path("state"));

        List<Project> projects = findProjectsByRepoUrl(repoUrl);
        touchWebhookDelivery(projects);

        findSubmissionByPullRequestUrl(pullRequestUrl).ifPresentOrElse(
                submission -> {
                    Timestamp reviewUpdatedAt = timestampAt(review, "submitted_at");
                    boolean updated = githubActivityService.updateReviewState(
                            submission,
                            deliveryId,
                            state,
                            textAt(review, "/user/login"),
                            text(review.path("html_url")),
                            reviewUpdatedAt
                    );
                    Long reviewId = longAt(review, "/id");
                    String reviewBody = text(review.path("body"));
                    if (reviewId != null && reviewBody != null && !reviewBody.isBlank()) {
                        githubActivityService.applyComment(
                                submission,
                                "pull_request_review",
                                "submitted".equals(action) ? "created" : "edited",
                                deliveryId,
                                reviewId,
                                textAt(review, "/user/login"),
                                textAt(review, "/user/avatar_url"),
                                reviewBody,
                                text(review.path("html_url")),
                                reviewUpdatedAt,
                                reviewUpdatedAt
                        );
                    }
                    if (updated) {
                        notifySubmissionReview(submission, number, state);
                    }
                },
                () -> notifyProjectOwners(projects, "GitHub PR #" + number + " received a review: " + humanizeReviewState(state) + ".", notificationTypeForReview(state))
        );
    }

    private void handlePullRequestComment(JsonNode root, String deliveryId, String eventType) {
        String action = text(root.path("action"));
        if (!Set.of("created", "edited", "deleted").contains(action)) {
            return;
        }
        if ("issue_comment".equals(eventType) && !root.path("issue").has("pull_request")) {
            return;
        }

        String repoUrl = textAt(root, "/repository/html_url");
        List<Project> projects = findProjectsByRepoUrl(repoUrl);
        touchWebhookDelivery(projects);

        String pullRequestUrl = "issue_comment".equals(eventType)
                ? textAt(root, "/issue/pull_request/html_url")
                : textAt(root, "/pull_request/html_url");
        int pullRequestNumber = "issue_comment".equals(eventType)
                ? root.path("issue").path("number").asInt()
                : root.path("pull_request").path("number").asInt();
        if ((pullRequestUrl == null || pullRequestUrl.isBlank()) && repoUrl != null && pullRequestNumber > 0) {
            pullRequestUrl = normalizeUrl(repoUrl) + "/pull/" + pullRequestNumber;
        }

        Optional<TaskSubmission> linkedSubmission = findSubmissionByPullRequestUrl(pullRequestUrl);
        if (linkedSubmission.isEmpty()) {
            return;
        }

        JsonNode comment = root.path("comment");
        Long commentId = longAt(comment, "/id");
        if (commentId == null) {
            return;
        }
        String author = textAt(comment, "/user/login");
        GithubActivityService.CommentResult result = githubActivityService.applyComment(
                linkedSubmission.get(),
                eventType,
                action,
                deliveryId,
                commentId,
                author,
                textAt(comment, "/user/avatar_url"),
                text(comment.path("body")),
                text(comment.path("html_url")),
                timestampAt(comment, "created_at"),
                timestampAt(comment, "updated_at")
        );
        if (result == GithubActivityService.CommentResult.CREATED) {
            notifySubmissionComment(linkedSubmission.get(), pullRequestNumber, author);
        }
    }

    private void notifySubmissionComment(TaskSubmission submission, int pullRequestNumber, String author) {
        Task task = submission.getTask();
        Project project = task == null ? null : task.getProject();
        User owner = project == null ? null : project.getOwner();
        User developer = submission.getUser();
        String taskTitle = task == null || task.getTitle() == null ? "submission" : task.getTitle();
        String targetPath = task == null || task.getId() == null ? null : "/task/" + task.getId();
        String authorLabel = author == null || author.isBlank() ? "Someone" : "@" + author;
        String message = authorLabel + " commented on GitHub PR #" + pullRequestNumber
                + " for '" + taskTitle + "'.";

        if (!sameGithubUser(owner, author)) {
            sendNotification(owner, message, "INFO", targetPath);
        }
        if (!sameUser(owner, developer) && !sameGithubUser(developer, author)) {
            sendNotification(developer, message, "INFO", targetPath);
        }
    }

    private void notifySubmissionPullRequest(
            TaskSubmission submission,
            String action,
            int pullRequestNumber,
            String pullRequestTitle,
            boolean merged
    ) {
        Task task = submission.getTask();
        Project project = task != null ? task.getProject() : null;
        User owner = project != null ? project.getOwner() : null;
        User developer = submission.getUser();
        String targetPath = task != null && task.getId() != null ? "/task/" + task.getId() : null;
        String taskTitle = task != null && task.getTitle() != null ? task.getTitle() : pullRequestTitle;
        String ownerMessage = buildSubmissionPullRequestMessage(action, pullRequestNumber, taskTitle, merged);
        String type = ("closed".equals(action) && merged) ? "SUCCESS" : "INFO";

        sendNotification(owner, ownerMessage, type, targetPath);

        if ("closed".equals(action) && developer != null && !sameUser(owner, developer)) {
            String developerMessage = merged
                    ? "Your GitHub PR #" + pullRequestNumber + " for '" + taskTitle + "' was merged."
                    : "Your GitHub PR #" + pullRequestNumber + " for '" + taskTitle + "' was closed.";
            sendNotification(developer, developerMessage, merged ? "SUCCESS" : "WARNING", targetPath);
        }
    }

    private void notifySubmissionReview(TaskSubmission submission, int pullRequestNumber, String state) {
        Task task = submission.getTask();
        Project project = task != null ? task.getProject() : null;
        String targetPath = task != null && task.getId() != null ? "/task/" + task.getId() : null;
        String taskTitle = task != null && task.getTitle() != null ? task.getTitle() : "your submission";
        String reviewState = humanizeReviewState(state);
        String type = notificationTypeForReview(state);

        sendNotification(
                submission.getUser(),
                "GitHub review on PR #" + pullRequestNumber + " for '" + taskTitle + "': " + reviewState + ".",
                type,
                targetPath
        );

        if (project != null && !sameUser(project.getOwner(), submission.getUser())) {
            sendNotification(
                    project.getOwner(),
                    "GitHub PR #" + pullRequestNumber + " for '" + taskTitle + "' received a review: " + reviewState + ".",
                    type,
                    targetPath
            );
        }
    }

    private void notifyProjectOwners(List<Project> projects, String message, String type) {
        for (Project project : projects) {
            String targetPath = project.getId() == null ? null : "/project/" + project.getId();
            sendNotification(project.getOwner(), message, type, targetPath);
        }
    }

    private void sendNotification(User user, String message, String type, String targetPath) {
        if (user == null) {
            return;
        }
        notificationService.sendNotification(user, message, type, targetPath);
    }

    private boolean sameGithubUser(User user, String githubUsername) {
        return user != null
                && user.getGithub_username() != null
                && githubUsername != null
                && user.getGithub_username().equalsIgnoreCase(githubUsername);
    }

    private String buildSubmissionPullRequestMessage(String action, int pullRequestNumber, String taskTitle, boolean merged) {
        if ("closed".equals(action)) {
            return merged
                    ? "GitHub PR #" + pullRequestNumber + " for '" + taskTitle + "' was merged."
                    : "GitHub PR #" + pullRequestNumber + " for '" + taskTitle + "' was closed without merge.";
        }

        return "GitHub PR #" + pullRequestNumber + " for '" + taskTitle + "' was " + humanizePullRequestAction(action) + ".";
    }

    private String buildProjectPullRequestMessage(String action, int pullRequestNumber, String pullRequestTitle, boolean merged) {
        String title = pullRequestTitle == null || pullRequestTitle.isBlank() ? "untitled pull request" : pullRequestTitle;
        if ("closed".equals(action)) {
            return merged
                    ? "GitHub PR #" + pullRequestNumber + " ('" + title + "') was merged."
                    : "GitHub PR #" + pullRequestNumber + " ('" + title + "') was closed without merge.";
        }

        return "GitHub PR #" + pullRequestNumber + " ('" + title + "') was " + humanizePullRequestAction(action) + ".";
    }

    private String humanizePullRequestAction(String action) {
        return switch (action == null ? "" : action) {
            case "opened" -> "opened";
            case "reopened" -> "reopened";
            case "synchronize" -> "updated";
            case "ready_for_review" -> "marked ready for review";
            case "converted_to_draft" -> "converted to draft";
            default -> action == null || action.isBlank() ? "updated" : action.replace('_', ' ');
        };
    }

    private String humanizeReviewState(String state) {
        return switch (state == null ? "" : state.toLowerCase(Locale.ROOT)) {
            case "approved" -> "approved";
            case "changes_requested" -> "changes requested";
            case "commented" -> "commented";
            case "dismissed" -> "dismissed";
            default -> state == null || state.isBlank() ? "review updated" : state.replace('_', ' ');
        };
    }

    private String notificationTypeForReview(String state) {
        return switch (state == null ? "" : state.toLowerCase(Locale.ROOT)) {
            case "approved" -> "SUCCESS";
            case "changes_requested" -> "WARNING";
            default -> "INFO";
        };
    }

    private void recordDelivery(String event, String deliveryId) {
        if (deliveryId == null || deliveryId.isBlank()) {
            return;
        }
        GithubWebhookDelivery delivery = new GithubWebhookDelivery();
        delivery.setDeliveryId(deliveryId);
        delivery.setEventType(event);
        githubWebhookDeliveryRepository.save(delivery);
    }

    private Timestamp timestampAt(JsonNode node, String field) {
        String value = text(node.path(field));
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Timestamp.from(Instant.parse(value));
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private void touchWebhookDelivery(List<Project> projects) {
        Date now = now();
        for (Project project : projects) {
            project.setGithubWebhookLastDeliveryAt(now);
            if (project.getGithubWebhookStatus() == null || project.getGithubWebhookStatus().isBlank()) {
                project.setGithubWebhookStatus(WEBHOOK_CONNECTED);
            }
            projectRepository.save(project);
        }
    }

    private Optional<TaskSubmission> findSubmissionByPullRequestUrl(String pullRequestUrl) {
        Set<String> variants = normalizedUrlVariants(pullRequestUrl);
        if (variants.isEmpty()) {
            return Optional.empty();
        }
        return taskSubmissionRepository.findFirstByPullRequestUrlNormalizedIn(variants);
    }

    private List<Project> findProjectsByRepoUrl(String repoUrl) {
        Set<String> variants = normalizedUrlVariants(repoUrl);
        if (variants.isEmpty()) {
            return List.of();
        }
        return projectRepository.findAllByGithubRepoNormalizedIn(variants);
    }

    private Set<String> normalizedUrlVariants(String url) {
        String normalized = normalizeUrl(url);
        if (normalized == null) {
            return Set.of();
        }

        Set<String> variants = new LinkedHashSet<>();
        variants.add(normalized);
        variants.add(normalized + "/");
        if (!normalized.endsWith(".git")) {
            variants.add(normalized + ".git");
        }
        return variants;
    }

    private String normalizeUrl(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }

        String normalized = url.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (normalized.endsWith(".git")) {
            normalized = normalized.substring(0, normalized.length() - 4);
        }
        return normalized.toLowerCase(Locale.ROOT);
    }

    private Project markWebhookFailed(Project project, String message) {
        project.setGithubWebhookStatus(WEBHOOK_FAILED);
        project.setGithubWebhookLastError(trimError(message));
        return projectRepository.save(project);
    }

    private String trimError(String message) {
        if (message == null || message.isBlank()) {
            return "GitHub webhook setup failed";
        }
        return message.length() <= 500 ? message : message.substring(0, 500);
    }

    private Optional<Long> findExistingHookId(RepoSlug repoSlug, String accessToken) {
        HttpRequest request = githubRequestBuilder(repoSlug.hooksUri(), accessToken).GET().build();
        HttpResponse<String> response = send(request);
        if (response.statusCode() >= 400) {
            throw githubRequestException("Unable to inspect GitHub webhooks", response.statusCode());
        }

        JsonNode hooks = readPayload(response.body());
        for (JsonNode hook : hooks) {
            String existingUrl = textAt(hook, "/config/url");
            if (normalizeUrl(webhookUrl).equals(normalizeUrl(existingUrl))) {
                Long id = longAt(hook, "/id");
                if (id != null) {
                    return Optional.of(id);
                }
            }
        }
        return Optional.empty();
    }

    private Long createHook(RepoSlug repoSlug, String accessToken) {
        HttpRequest request = githubRequestBuilder(repoSlug.hooksUri(), accessToken)
                .POST(HttpRequest.BodyPublishers.ofString(hookRequestBody()))
                .build();
        return readHookId(sendHookRequest(request, "Unable to create GitHub webhook"));
    }

    private Long updateHook(RepoSlug repoSlug, Long hookId, String accessToken) {
        HttpRequest request = githubRequestBuilder(repoSlug.hookUri(hookId), accessToken)
                .method("PATCH", HttpRequest.BodyPublishers.ofString(hookRequestBody()))
                .build();
        return readHookId(sendHookRequest(request, "Unable to update GitHub webhook"));
    }

    private String sendHookRequest(HttpRequest request, String errorMessage) {
        HttpResponse<String> response = send(request);
        if (response.statusCode() >= 400) {
            throw githubRequestException(errorMessage, response.statusCode());
        }
        return response.body();
    }

    private IllegalArgumentException githubRequestException(String fallbackMessage, int statusCode) {
        String message = switch (statusCode) {
            case 401 -> "Project owner must reconnect GitHub with webhook permissions";
            case 403 -> "Project owner needs admin access to the GitHub repository";
            case 404 -> "GitHub repository was not found or is not accessible";
            case 422 -> "GitHub rejected the webhook configuration";
            default -> fallbackMessage;
        };
        return new IllegalArgumentException(message);
    }

    private Long readHookId(String body) {
        Long hookId = longAt(readPayload(body), "/id");
        if (hookId == null) {
            throw new IllegalArgumentException("GitHub webhook response did not include a hook id");
        }
        return hookId;
    }

    private String hookRequestBody() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("url", webhookUrl.trim());
        config.put("content_type", "json");
        config.put("secret", webhookSecret.trim());
        config.put("insecure_ssl", "0");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", "web");
        body.put("active", true);
        body.put("events", List.of(
                "pull_request",
                "pull_request_review",
                "pull_request_review_comment",
                "issue_comment",
                "issues"
        ));
        body.put("config", config);

        try {
            return objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Unable to build GitHub webhook request", e);
        }
    }

    private HttpRequest.Builder githubRequestBuilder(URI uri, String accessToken) {
        return HttpRequest.newBuilder()
                .uri(uri)
                .header(HttpHeaders.ACCEPT, "application/vnd.github+json")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .header(HttpHeaders.USER_AGENT, GITHUB_USER_AGENT)
                .header("X-GitHub-Api-Version", "2022-11-28");
    }

    private HttpResponse<String> send(HttpRequest request) {
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new IllegalArgumentException("Unable to contact GitHub");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalArgumentException("GitHub request was interrupted");
        }
    }

    private RepoSlug parseRepoSlug(String githubRepo) {
        if (githubRepo == null || githubRepo.isBlank()) {
            throw new IllegalArgumentException("Project repository is required");
        }

        URI uri = URI.create(githubRepo.trim());
        if (uri.getHost() == null || !"github.com".equalsIgnoreCase(uri.getHost())) {
            throw new IllegalArgumentException("Project repository must be a GitHub URL");
        }

        List<String> segments = new ArrayList<>();
        for (String segment : uri.getPath().split("/")) {
            if (!segment.isBlank()) {
                segments.add(segment);
            }
        }
        if (segments.size() != 2) {
            throw new IllegalArgumentException("Project repository must point to a GitHub repository");
        }

        String repo = segments.get(1).endsWith(".git")
                ? segments.get(1).substring(0, segments.get(1).length() - 4)
                : segments.get(1);
        return new RepoSlug(segments.get(0), repo);
    }

    private JsonNode readPayload(String payload) {
        try {
            return objectMapper.readTree(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Unable to parse GitHub webhook payload");
        }
    }

    private String text(JsonNode node) {
        return node == null || node.isMissingNode() || node.isNull() ? "" : node.asText("");
    }

    private String textAt(JsonNode node, String pointer) {
        return text(node.at(pointer));
    }

    private Long longAt(JsonNode node, String pointer) {
        JsonNode value = node.at(pointer);
        return value.isNumber() ? value.asLong() : null;
    }

    private boolean sameUser(User first, User second) {
        if (first == null || second == null || first.getId() == null || second.getId() == null) {
            return false;
        }
        return first.getId().equals(second.getId());
    }

    private Date now() {
        return new Date(System.currentTimeMillis());
    }

    private record RepoSlug(String owner, String repo) {
        URI hooksUri() {
            return URI.create(GITHUB_API_URL + "/repos/" + encode(owner) + "/" + encode(repo) + "/hooks?per_page=100");
        }

        URI hookUri(Long hookId) {
            return URI.create(GITHUB_API_URL + "/repos/" + encode(owner) + "/" + encode(repo) + "/hooks/" + hookId);
        }

        private static String encode(String value) {
            return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
        }
    }
}
