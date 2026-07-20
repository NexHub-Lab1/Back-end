package com.nexhub.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexhub.backend.model.Project;
import com.nexhub.backend.model.Task;
import com.nexhub.backend.model.TaskSubmission;
import com.nexhub.backend.model.User;
import com.nexhub.backend.repository.ProjectRepository;
import com.nexhub.backend.repository.GithubWebhookDeliveryRepository;
import com.nexhub.backend.repository.TaskAssignmentRepository;
import com.nexhub.backend.repository.TaskRepository;
import com.nexhub.backend.repository.TaskSubmissionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class GithubWebhookServiceTest {
    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private TaskSubmissionRepository taskSubmissionRepository;

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private TaskAssignmentRepository taskAssignmentRepository;

    @Mock
    private NotificationService notificationService;

    @Mock
    private GithubActivityService githubActivityService;

    @Mock
    private GithubWebhookDeliveryRepository githubWebhookDeliveryRepository;

    private GithubWebhookVerifier verifier;
    private GithubWebhookService service;

    @BeforeEach
    void setUp() {
        verifier = new GithubWebhookVerifier();
        ReflectionTestUtils.setField(verifier, "webhookSecret", "test-secret");
        service = new GithubWebhookService(
                projectRepository,
                taskSubmissionRepository,
                taskRepository,
                taskAssignmentRepository,
                notificationService,
                githubActivityService,
                githubWebhookDeliveryRepository,
                verifier,
                new ObjectMapper()
        );
        ReflectionTestUtils.setField(service, "webhookUrl", "https://nexhub.example/api/github/webhooks");
        ReflectionTestUtils.setField(service, "webhookSecret", "test-secret");
    }

    @Test
    void completedIssueClosesTaskWithoutChangingFunding() {
        User owner = sampleUser(1L, "owner@nexhub.dev");
        Project project = sampleProject(owner);
        Task task = linkedTask(project, "in_progress", "funded");
        String payload = issuePayload("closed", "closed", "completed", false);
        prepareIssueWebhook(task, project);

        service.processWebhook("issues", "delivery-completed", signature(payload), payload);

        assertThat(task.getStatus()).isEqualTo("completed");
        assertThat(task.getFundingStatus()).isEqualTo("funded");
        assertThat(task.getGithubIssueState()).isEqualTo("closed");
        verify(notificationService).sendNotification(eq(owner), contains("was completed"), eq("SUCCESS"), eq("/task/20"));
    }

    @Test
    void reopenedIssueMovesTaskBackToInProgress() {
        User owner = sampleUser(1L, "owner@nexhub.dev");
        Project project = sampleProject(owner);
        Task task = linkedTask(project, "completed", "funded");
        String payload = issuePayload("reopened", "open", null, false);
        prepareIssueWebhook(task, project);

        service.processWebhook("issues", "delivery-reopened", signature(payload), payload);

        assertThat(task.getStatus()).isEqualTo("in_progress");
        assertThat(task.getGithubIssueState()).isEqualTo("open");
    }

    @Test
    void reopenedIssueDoesNotReactivateCancelledTask() {
        User owner = sampleUser(1L, "owner@nexhub.dev");
        Project project = sampleProject(owner);
        Task task = linkedTask(project, "cancelled", "refunded");
        String payload = issuePayload("reopened", "open", null, false);
        prepareIssueWebhook(task, project);

        service.processWebhook("issues", "delivery-cancelled", signature(payload), payload);

        assertThat(task.getStatus()).isEqualTo("cancelled");
        assertThat(task.getFundingStatus()).isEqualTo("refunded");
    }

    @Test
    void reopenedIssueDoesNotRevertReleasedTask() {
        User owner = sampleUser(1L, "owner@nexhub.dev");
        Project project = sampleProject(owner);
        Task task = linkedTask(project, "completed", "released");
        String payload = issuePayload("reopened", "open", null, false);
        prepareIssueWebhook(task, project);

        service.processWebhook("issues", "delivery-released", signature(payload), payload);

        assertThat(task.getStatus()).isEqualTo("completed");
        assertThat(task.getFundingStatus()).isEqualTo("released");
        assertThat(task.getGithubIssueState()).isEqualTo("open");
    }

    @Test
    void notPlannedIssueKeepsTaskAndFundingState() {
        User owner = sampleUser(1L, "owner@nexhub.dev");
        Project project = sampleProject(owner);
        Task task = linkedTask(project, "in_progress", "funded");
        String payload = issuePayload("closed", "closed", "not_planned", false);
        prepareIssueWebhook(task, project);

        service.processWebhook("issues", "delivery-not-planned", signature(payload), payload);

        assertThat(task.getStatus()).isEqualTo("in_progress");
        assertThat(task.getFundingStatus()).isEqualTo("funded");
        verify(notificationService).sendNotification(eq(owner), contains("Funding was not changed"), eq("WARNING"), eq("/task/20"));
    }

    @Test
    void unrelatedIssueIsIgnored() {
        String payload = issuePayload("closed", "closed", "completed", false);
        when(taskRepository.findByGithubIssueId(9001L)).thenReturn(Optional.empty());

        service.processWebhook("issues", "delivery-unrelated", signature(payload), payload);

        verify(taskRepository, never()).save(any());
        verify(notificationService, never()).sendNotification(any(), any(), any(), any());
    }

    @Test
    void pullRequestRepresentedAsIssueIsIgnored() {
        String payload = issuePayload("closed", "closed", "completed", true);

        service.processWebhook("issues", "delivery-pr-issue", signature(payload), payload);

        verify(taskRepository, never()).findByGithubIssueId(any());
    }

    @Test
    void repeatedDeliveryDoesNotDuplicateNotifications() {
        User owner = sampleUser(1L, "owner@nexhub.dev");
        Project project = sampleProject(owner);
        Task task = linkedTask(project, "in_progress", "funded");
        String payload = issuePayload("closed", "closed", "completed", false);
        prepareIssueWebhook(task, project);

        service.processWebhook("issues", "delivery-repeat", signature(payload), payload);
        service.processWebhook("issues", "delivery-repeat", signature(payload), payload);

        verify(notificationService, times(1)).sendNotification(eq(owner), contains("was completed"), eq("SUCCESS"), eq("/task/20"));
    }

    @Test
    void configuredWebhookIncludesIssuesEvent() {
        String requestBody = ReflectionTestUtils.invokeMethod(service, "hookRequestBody");
        assertThat(requestBody)
                .contains("\"issues\"")
                .contains("\"issue_comment\"")
                .contains("\"pull_request_review_comment\"");
    }

    @Test
    void linkedPullRequestCommentIsStoredAndNotifiesRelevantUsers() {
        User owner = sampleUser(1L, "owner@nexhub.dev");
        owner.setGithub_username("owner-gh");
        User developer = sampleUser(2L, "developer@nexhub.dev");
        developer.setGithub_username("developer-gh");
        Project project = sampleProject(owner);
        TaskSubmission submission = sampleSubmission(project, developer);
        String payload = pullRequestCommentPayload("created", true);

        when(projectRepository.findAllByGithubRepoNormalizedIn(any())).thenReturn(List.of(project));
        when(projectRepository.save(project)).thenReturn(project);
        when(taskSubmissionRepository.findFirstByPullRequestUrlNormalizedIn(any())).thenReturn(Optional.of(submission));
        when(githubActivityService.applyComment(
                eq(submission), eq("issue_comment"), eq("created"), eq("delivery-comment"),
                eq(7001L), eq("reviewer-gh"), any(), eq("Please add a regression test."),
                any(), any(), any()
        )).thenReturn(GithubActivityService.CommentResult.CREATED);

        service.processWebhook("issue_comment", "delivery-comment", signature(payload), payload);

        verify(notificationService).sendNotification(eq(owner), contains("@reviewer-gh commented"), eq("INFO"), eq("/task/20"));
        verify(notificationService).sendNotification(eq(developer), contains("@reviewer-gh commented"), eq("INFO"), eq("/task/20"));
    }

    @Test
    void commentOnUnlinkedPullRequestIsIgnored() {
        Project project = sampleProject(sampleUser(1L, "owner@nexhub.dev"));
        String payload = pullRequestCommentPayload("created", true);
        when(projectRepository.findAllByGithubRepoNormalizedIn(any())).thenReturn(List.of(project));
        when(projectRepository.save(project)).thenReturn(project);
        when(taskSubmissionRepository.findFirstByPullRequestUrlNormalizedIn(any())).thenReturn(Optional.empty());

        service.processWebhook("issue_comment", "delivery-unlinked", signature(payload), payload);

        verify(githubActivityService, never()).applyComment(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
        verify(notificationService, never()).sendNotification(any(), any(), any(), any());
    }

    @Test
    void ordinaryIssueCommentIsIgnored() {
        String payload = pullRequestCommentPayload("created", false);

        service.processWebhook("issue_comment", "delivery-ordinary-issue", signature(payload), payload);

        verify(taskSubmissionRepository, never()).findFirstByPullRequestUrlNormalizedIn(any());
        verify(githubActivityService, never()).applyComment(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void reviewUpdatesTechnicalStateWithoutChangingBusinessStatus() {
        User owner = sampleUser(1L, "owner@nexhub.dev");
        User developer = sampleUser(2L, "developer@nexhub.dev");
        Project project = sampleProject(owner);
        TaskSubmission submission = sampleSubmission(project, developer);
        String payload = pullRequestReviewPayload();

        when(projectRepository.findAllByGithubRepoNormalizedIn(any())).thenReturn(List.of(project));
        when(projectRepository.save(project)).thenReturn(project);
        when(taskSubmissionRepository.findFirstByPullRequestUrlNormalizedIn(any())).thenReturn(Optional.of(submission));
        when(githubActivityService.updateReviewState(
                eq(submission), eq("delivery-review"), eq("changes_requested"),
                eq("reviewer-gh"), any(), any()
        )).thenReturn(true);

        service.processWebhook("pull_request_review", "delivery-review", signature(payload), payload);

        assertThat(submission.getStatus()).isEqualTo("submitted");
        verify(notificationService).sendNotification(eq(developer), contains("changes requested"), eq("WARNING"), eq("/task/20"));
    }

    @Test
    void repeatedGlobalDeliveryIsIgnored() {
        String payload = pullRequestCommentPayload("created", true);
        when(githubWebhookDeliveryRepository.existsById("delivery-repeat-global")).thenReturn(true);

        service.processWebhook("issue_comment", "delivery-repeat-global", signature(payload), payload);

        verify(githubActivityService, never()).applyComment(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
        verify(githubWebhookDeliveryRepository, never()).save(any());
    }

    @Test
    void pullRequestMergeNotifiesSubmissionUsersWithoutChangingSubmissionStatus() {
        User owner = sampleUser(1L, "owner@nexhub.dev");
        User developer = sampleUser(2L, "developer@nexhub.dev");
        Project project = sampleProject(owner);
        TaskSubmission submission = sampleSubmission(project, developer);
        String payload = """
                {
                  "action": "closed",
                  "repository": {
                    "html_url": "https://github.com/nexhub/backend"
                  },
                  "pull_request": {
                    "html_url": "https://github.com/nexhub/backend/pull/24",
                    "number": 24,
                    "title": "Add payments",
                    "merged": true
                  }
                }
                """;

        when(projectRepository.findAllByGithubRepoNormalizedIn(any())).thenReturn(List.of(project));
        when(projectRepository.save(project)).thenReturn(project);
        when(taskSubmissionRepository.findFirstByPullRequestUrlNormalizedIn(any())).thenReturn(Optional.of(submission));

        service.processWebhook("pull_request", "delivery-1", signature(payload), payload);

        verify(notificationService).sendNotification(
                eq(owner),
                contains("GitHub PR #24 for 'Implement webhooks' was merged."),
                eq("SUCCESS"),
                eq("/task/20")
        );
        verify(notificationService).sendNotification(
                eq(developer),
                contains("Your GitHub PR #24 for 'Implement webhooks' was merged."),
                eq("SUCCESS"),
                eq("/task/20")
        );
    }

    private String signature(String payload) {
        return "sha256=" + verifier.sign(payload);
    }

    private void prepareIssueWebhook(Task task, Project project) {
        when(taskRepository.findByGithubIssueId(9001L)).thenReturn(Optional.of(task));
        when(projectRepository.findAllByGithubRepoNormalizedIn(any())).thenReturn(List.of(project));
        when(projectRepository.save(project)).thenReturn(project);
        when(taskRepository.save(task)).thenReturn(task);
        lenient().when(taskAssignmentRepository.findByTask_Id(task.getId())).thenReturn(List.of());
    }

    private static Task linkedTask(Project project, String status, String fundingStatus) {
        Task task = new Task();
        setField(task, "id", 20L);
        task.setProject(project);
        task.setTitle("Implement webhooks");
        task.setStatus(status);
        task.setFundingStatus(fundingStatus);
        task.setGithubIssueId(9001L);
        task.setGithubIssueNumber(42);
        task.setGithubIssueUrl("https://github.com/nexhub/backend/issues/42");
        task.setGithubIssueState("open");
        return task;
    }

    private static String issuePayload(String action, String state, String stateReason, boolean pullRequest) {
        String reasonJson = stateReason == null ? "null" : "\"" + stateReason + "\"";
        String pullRequestJson = pullRequest ? ", \"pull_request\": {\"url\": \"https://api.github.com/repos/nexhub/backend/pulls/42\"}" : "";
        return """
                {
                  "action": "%s",
                  "repository": {"html_url": "https://github.com/nexhub/backend"},
                  "issue": {
                    "id": 9001,
                    "number": 42,
                    "state": "%s",
                    "state_reason": %s%s
                  }
                }
                """.formatted(action, state, reasonJson, pullRequestJson);
    }

    private static String pullRequestCommentPayload(String action, boolean pullRequest) {
        String pullRequestJson = pullRequest
                ? ", \"pull_request\": {\"html_url\": \"https://github.com/nexhub/backend/pull/24\"}"
                : "";
        return """
                {
                  "action": "%s",
                  "repository": {"html_url": "https://github.com/nexhub/backend"},
                  "issue": {"number": 24%s},
                  "comment": {
                    "id": 7001,
                    "body": "Please add a regression test.",
                    "html_url": "https://github.com/nexhub/backend/pull/24#issuecomment-7001",
                    "created_at": "2026-07-20T12:00:00Z",
                    "updated_at": "2026-07-20T12:00:00Z",
                    "user": {
                      "login": "reviewer-gh",
                      "avatar_url": "https://avatars.githubusercontent.com/u/1"
                    }
                  }
                }
                """.formatted(action, pullRequestJson);
    }

    private static String pullRequestReviewPayload() {
        return """
                {
                  "action": "submitted",
                  "repository": {"html_url": "https://github.com/nexhub/backend"},
                  "pull_request": {
                    "number": 24,
                    "html_url": "https://github.com/nexhub/backend/pull/24"
                  },
                  "review": {
                    "id": 8101,
                    "state": "changes_requested",
                    "body": "Please address the comments.",
                    "html_url": "https://github.com/nexhub/backend/pull/24#pullrequestreview-8101",
                    "submitted_at": "2026-07-20T12:00:00Z",
                    "user": {"login": "reviewer-gh"}
                  }
                }
                """;
    }

    private static Project sampleProject(User owner) {
        Project project = new Project();
        setField(project, "id", 10L);
        project.setOwner(owner);
        project.setName("NexHub");
        project.setGithubRepo("https://github.com/nexhub/backend");
        return project;
    }

    private static TaskSubmission sampleSubmission(Project project, User developer) {
        Task task = new Task();
        setField(task, "id", 20L);
        task.setProject(project);
        task.setTitle("Implement webhooks");

        TaskSubmission submission = new TaskSubmission();
        setField(submission, "id", 30L);
        submission.setTask(task);
        submission.setUser(developer);
        submission.setPullRequestUrl("https://github.com/nexhub/backend/pull/24");
        submission.setStatus("submitted");
        return submission;
    }

    private static User sampleUser(Long id, String email) {
        User user = new User();
        setField(user, "id", id);
        user.setEmail(email);
        user.setUsername(email.substring(0, email.indexOf('@')));
        return user;
    }

    private static void setField(Object target, String name, Object value) {
        try {
            var field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }
}
