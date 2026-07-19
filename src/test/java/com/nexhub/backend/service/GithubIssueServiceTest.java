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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.sql.Date;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GithubIssueServiceTest {
    @Mock
    private TaskRepository taskRepository;
    @Mock
    private TaskAssignmentRepository assignmentRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private GithubIssueClient githubIssueClient;

    private GithubIssueService service;

    @BeforeEach
    void setUp() {
        service = new GithubIssueService(taskRepository, assignmentRepository, userRepository, githubIssueClient);
    }

    @Test
    void firstAssignmentCreatesOneIssue() {
        Task task = sampleTask();
        TaskAssignment assignment = assignment(task, 2L, "developer", "octodev");
        GithubIssue created = new GithubIssue(9001L, 42, "https://github.com/nexhub/backend/issues/42", "open");
        prepareTask(task, List.of(assignment));
        when(githubIssueClient.findByTaskMarker(anyString(), anyString(), contains("nexhub-task-id:20")))
                .thenReturn(Optional.empty());
        when(githubIssueClient.createIssue(anyString(), anyString(), contains("Task #20"), contains("Deliverables")))
                .thenReturn(created);
        when(githubIssueClient.addAssignees(anyString(), anyString(), eq(42), eq(List.of("octodev"))))
                .thenReturn(List.of("octodev"));
        when(githubIssueClient.updateIssueBody(anyString(), anyString(), eq(42), contains("@octodev")))
                .thenReturn(created);

        Task result = service.syncTaskIssue(20L);

        assertThat(result.getGithubIssueId()).isEqualTo(9001L);
        assertThat(result.getGithubIssueNumber()).isEqualTo(42);
        assertThat(result.getGithubIssueSyncStatus()).isEqualTo("synced");
        verify(githubIssueClient).createIssue(anyString(), anyString(), contains("Task #20"), contains("nexhub-task-id:20"));
    }

    @Test
    void secondCollaborativeAssignmentReusesIssue() {
        Task task = sampleTask();
        task.setGithubIssueId(9001L);
        task.setGithubIssueNumber(42);
        task.setGithubIssueUrl("https://github.com/nexhub/backend/issues/42");
        task.setGithubIssueState("open");
        TaskAssignment first = assignment(task, 2L, "developer", "octodev");
        TaskAssignment second = assignment(task, 3L, "collaborator", "octocollab");
        prepareTask(task, List.of(first, second));
        when(githubIssueClient.addAssignees(anyString(), anyString(), eq(42), eq(List.of("octodev", "octocollab"))))
                .thenReturn(List.of("octodev", "octocollab"));
        when(githubIssueClient.updateIssueBody(anyString(), anyString(), eq(42), contains("@octocollab")))
                .thenReturn(new GithubIssue(9001L, 42, task.getGithubIssueUrl(), "open"));

        service.syncTaskIssue(20L);

        verify(githubIssueClient, never()).createIssue(anyString(), anyString(), anyString(), anyString());
        verify(githubIssueClient).updateIssueBody(anyString(), anyString(), eq(42), contains("collaborator"));
    }

    @Test
    void githubFailureIsStoredWithoutDeletingAssignment() {
        Task task = sampleTask();
        TaskAssignment assignment = assignment(task, 2L, "developer", "octodev");
        prepareTask(task, List.of(assignment));
        when(githubIssueClient.findByTaskMarker(anyString(), anyString(), anyString()))
                .thenThrow(new IllegalArgumentException("GitHub unavailable"));

        Task result = service.syncTaskIssue(20L);

        assertThat(result.getGithubIssueSyncStatus()).isEqualTo("failed");
        assertThat(result.getGithubIssueLastError()).contains("GitHub unavailable");
        assertThat(assignmentRepository.findByTask_Id(20L)).containsExactly(assignment);
        verify(assignmentRepository, never()).delete(assignment);
    }

    private void prepareTask(Task task, List<TaskAssignment> assignments) {
        when(taskRepository.findByIdForGithubIssueSync(20L)).thenReturn(Optional.of(task));
        when(assignmentRepository.findByTask_Id(20L)).thenReturn(assignments);
        when(taskRepository.save(task)).thenReturn(task);
    }

    private static Task sampleTask() {
        User owner = user(1L, "owner", "octoowner");
        owner.setGithub_access_token("owner-token");
        Project project = new Project();
        setId(project, 10L);
        project.setOwner(owner);
        project.setGithubRepo("https://github.com/nexhub/backend");

        Task task = new Task();
        setId(task, 20L);
        task.setProject(project);
        task.setTitle("Implement issue sync");
        task.setDescription("Create one shared issue");
        task.setDeliverables("Tests and implementation");
        task.setRewardAmount(BigDecimal.valueOf(15000));
        task.setRewardCurrency("ARS");
        task.setDeadline(Date.valueOf("2026-08-01"));
        task.setCollaborative(true);
        return task;
    }

    private static TaskAssignment assignment(Task task, Long id, String username, String githubUsername) {
        TaskAssignment assignment = new TaskAssignment();
        setId(assignment, id + 100);
        assignment.setTask(task);
        assignment.setUser(user(id, username, githubUsername));
        assignment.setStatus("active");
        return assignment;
    }

    private static User user(Long id, String username, String githubUsername) {
        User user = new User();
        setId(user, id);
        user.setUsername(username);
        user.setEmail(username + "@nexhub.dev");
        user.setGithub_username(githubUsername);
        return user;
    }

    private static void setId(Object target, Long id) {
        try {
            var field = target.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(target, id);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }
}
