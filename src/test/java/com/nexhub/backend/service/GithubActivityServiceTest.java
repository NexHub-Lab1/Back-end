package com.nexhub.backend.service;

import com.nexhub.backend.dto.github.GithubPullRequestCommentResponse;
import com.nexhub.backend.model.GithubPullRequestComment;
import com.nexhub.backend.model.Project;
import com.nexhub.backend.model.Task;
import com.nexhub.backend.model.TaskSubmission;
import com.nexhub.backend.model.User;
import com.nexhub.backend.repository.GithubPullRequestCommentRepository;
import com.nexhub.backend.repository.TaskAssignmentRepository;
import com.nexhub.backend.repository.TaskRepository;
import com.nexhub.backend.repository.TaskSubmissionRepository;
import com.nexhub.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GithubActivityServiceTest {
    @Mock
    private GithubPullRequestCommentRepository commentRepository;
    @Mock
    private TaskSubmissionRepository taskSubmissionRepository;
    @Mock
    private TaskRepository taskRepository;
    @Mock
    private TaskAssignmentRepository taskAssignmentRepository;
    @Mock
    private UserRepository userRepository;

    private GithubActivityService service;

    @BeforeEach
    void setUp() {
        service = new GithubActivityService(
                commentRepository,
                taskSubmissionRepository,
                taskRepository,
                taskAssignmentRepository,
                userRepository
        );
    }

    @Test
    void createEditAndDeleteCommentMaintainOneStoredActivity() {
        TaskSubmission submission = sampleSubmission();
        Timestamp createdAt = Timestamp.from(Instant.parse("2026-07-20T12:00:00Z"));
        when(commentRepository.findByEventTypeAndGithubCommentId("issue_comment", 91L))
                .thenReturn(Optional.empty());

        GithubActivityService.CommentResult created = service.applyComment(
                submission, "issue_comment", "created", "delivery-1", 91L,
                "reviewer", null, "First body", "https://github.com/org/repo/pull/4#issuecomment-91",
                createdAt, createdAt
        );

        assertThat(created).isEqualTo(GithubActivityService.CommentResult.CREATED);
        verify(commentRepository).save(any(GithubPullRequestComment.class));

        GithubPullRequestComment stored = new GithubPullRequestComment();
        stored.setSubmission(submission);
        stored.setEventType("issue_comment");
        stored.setGithubCommentId(91L);
        stored.setLastDeliveryId("delivery-1");
        when(commentRepository.findByEventTypeAndGithubCommentId("issue_comment", 91L))
                .thenReturn(Optional.of(stored));

        GithubActivityService.CommentResult edited = service.applyComment(
                submission, "issue_comment", "edited", "delivery-2", 91L,
                "reviewer", null, "Edited body", "https://github.com/org/repo/pull/4#issuecomment-91",
                createdAt, Timestamp.from(Instant.parse("2026-07-20T12:05:00Z"))
        );
        GithubActivityService.CommentResult deleted = service.applyComment(
                submission, "issue_comment", "deleted", "delivery-3", 91L,
                "reviewer", null, "Edited body", "https://github.com/org/repo/pull/4#issuecomment-91",
                createdAt, Timestamp.from(Instant.parse("2026-07-20T12:06:00Z"))
        );

        assertThat(edited).isEqualTo(GithubActivityService.CommentResult.UPDATED);
        assertThat(deleted).isEqualTo(GithubActivityService.CommentResult.DELETED);
        assertThat(stored.getBody()).isEqualTo("Edited body");
        assertThat(stored.getDeleted()).isTrue();
    }

    @Test
    void duplicateCommentDeliveryHasNoEffect() {
        GithubPullRequestComment stored = new GithubPullRequestComment();
        stored.setLastDeliveryId("same-delivery");
        when(commentRepository.findByEventTypeAndGithubCommentId("issue_comment", 91L))
                .thenReturn(Optional.of(stored));

        GithubActivityService.CommentResult result = service.applyComment(
                sampleSubmission(), "issue_comment", "edited", "same-delivery", 91L,
                "reviewer", null, "Body", null, null, null
        );

        assertThat(result).isEqualTo(GithubActivityService.CommentResult.DUPLICATE);
        verify(commentRepository, never()).save(any());
    }

    @Test
    void developerSeesOnlyActivityForOwnSubmission() {
        TaskSubmission submission = sampleSubmission();
        User developer = submission.getUser();
        Task task = submission.getTask();
        GithubPullRequestComment comment = new GithubPullRequestComment();
        setField(comment, "id", 51L);
        comment.setSubmission(submission);
        comment.setEventType("issue_comment");
        comment.setAuthorUsername("reviewer");
        comment.setBody("Looks good");

        when(userRepository.findByEmail(developer.getEmail())).thenReturn(Optional.of(developer));
        when(taskRepository.findById(task.getId())).thenReturn(Optional.of(task));
        when(taskAssignmentRepository.existsByTask_IdAndUser_Id(task.getId(), developer.getId())).thenReturn(true);
        when(commentRepository.findTop50BySubmission_Task_IdAndSubmission_User_IdAndDeletedFalseOrderByGithubCreatedAtDesc(
                task.getId(), developer.getId()
        )).thenReturn(List.of(comment));

        List<GithubPullRequestCommentResponse> result = service.getTaskActivity(task.getId(), developer.getEmail());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).authorUsername()).isEqualTo("reviewer");
    }

    @Test
    void unrelatedUserCannotReadTaskActivity() {
        TaskSubmission submission = sampleSubmission();
        Task task = submission.getTask();
        User outsider = sampleUser(88L, "outsider@nexhub.dev");
        when(userRepository.findByEmail(outsider.getEmail())).thenReturn(Optional.of(outsider));
        when(taskRepository.findById(task.getId())).thenReturn(Optional.of(task));

        assertThatThrownBy(() -> service.getTaskActivity(task.getId(), outsider.getEmail()))
                .isInstanceOf(AccessDeniedException.class);
    }

    private static TaskSubmission sampleSubmission() {
        User owner = sampleUser(1L, "owner@nexhub.dev");
        User developer = sampleUser(2L, "developer@nexhub.dev");
        Project project = new Project();
        setField(project, "id", 10L);
        project.setOwner(owner);
        Task task = new Task();
        setField(task, "id", 20L);
        task.setProject(project);
        task.setTitle("Implement webhooks");
        TaskSubmission submission = new TaskSubmission();
        setField(submission, "id", 30L);
        submission.setTask(task);
        submission.setUser(developer);
        submission.setPullRequestUrl("https://github.com/org/repo/pull/4");
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
