package com.nexhub.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexhub.backend.model.Project;
import com.nexhub.backend.model.Task;
import com.nexhub.backend.model.TaskSubmission;
import com.nexhub.backend.model.User;
import com.nexhub.backend.repository.ProjectRepository;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GithubWebhookServiceTest {
    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private TaskSubmissionRepository taskSubmissionRepository;

    @Mock
    private NotificationService notificationService;

    private GithubWebhookVerifier verifier;
    private GithubWebhookService service;

    @BeforeEach
    void setUp() {
        verifier = new GithubWebhookVerifier();
        ReflectionTestUtils.setField(verifier, "webhookSecret", "test-secret");
        service = new GithubWebhookService(
                projectRepository,
                taskSubmissionRepository,
                notificationService,
                verifier,
                new ObjectMapper()
        );
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
