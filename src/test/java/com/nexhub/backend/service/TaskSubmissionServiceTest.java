package com.nexhub.backend.service;

import com.nexhub.backend.dto.tasksubmission.TaskSubmissionUpdateRequest;
import com.nexhub.backend.model.Project;
import com.nexhub.backend.model.Task;
import com.nexhub.backend.model.TaskAssignment;
import com.nexhub.backend.model.TaskSubmission;
import com.nexhub.backend.model.User;
import com.nexhub.backend.repository.TaskAssignmentRepository;
import com.nexhub.backend.repository.TaskSubmissionRepository;
import com.nexhub.backend.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskSubmissionServiceTest {
    @Mock
    private TaskSubmissionRepository taskSubmissionRepository;

    @Mock
    private TaskAssignmentRepository taskAssignmentRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private NotificationService notificationService;

    @Mock
    private PaymentService paymentService;

    @InjectMocks
    private TaskSubmissionService taskSubmissionService;

    @Test
    void approvedRewardUsesAuthenticatedProjectOwnerInsteadOfRequestedReviewerId() {
        User owner = sampleUser(1L, "owner@nexhub.dev");
        User developer = sampleUser(2L, "developer@nexhub.dev");
        TaskSubmission submission = sampleSubmission(owner, developer);

        when(taskSubmissionRepository.findById(40L)).thenReturn(Optional.of(submission));
        when(userRepository.findByEmail(owner.getEmail())).thenReturn(Optional.of(owner));
        when(taskSubmissionRepository.save(any(TaskSubmission.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        taskSubmissionService.updateSubmission(
                new TaskSubmissionUpdateRequest(40L, null, "approved", null, 99L, null),
                owner.getEmail()
        );

        assertThat(submission.getReviewer()).isEqualTo(owner);
        verify(paymentService).releaseRewardForApprovedSubmission(submission);
    }

    @Test
    void nonOwnerCannotApproveSubmissionOrReleaseReward() {
        User owner = sampleUser(1L, "owner@nexhub.dev");
        User developer = sampleUser(2L, "developer@nexhub.dev");
        User attacker = sampleUser(3L, "attacker@nexhub.dev");
        TaskSubmission submission = sampleSubmission(owner, developer);

        when(taskSubmissionRepository.findById(40L)).thenReturn(Optional.of(submission));
        when(userRepository.findByEmail(attacker.getEmail())).thenReturn(Optional.of(attacker));

        assertThatThrownBy(() -> taskSubmissionService.updateSubmission(
                new TaskSubmissionUpdateRequest(40L, null, "approved", null, owner.getId(), null),
                attacker.getEmail()
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Only the project owner can review this submission");

        verify(paymentService, never()).releaseRewardForApprovedSubmission(any(TaskSubmission.class));
    }

    private static TaskSubmission sampleSubmission(User owner, User developer) {
        Project project = new Project();
        setField(project, "id", 10L);
        project.setOwner(owner);
        project.setName("NexHub");

        Task task = new Task();
        setField(task, "id", 20L);
        task.setProject(project);
        task.setTitle("Secure reward payout");

        TaskAssignment assignment = new TaskAssignment();
        setField(assignment, "id", 30L);
        assignment.setTask(task);
        assignment.setUser(developer);
        assignment.setStatus("active");

        TaskSubmission submission = new TaskSubmission();
        setField(submission, "id", 40L);
        submission.setTask(task);
        submission.setAssignment(assignment);
        submission.setUser(developer);
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
