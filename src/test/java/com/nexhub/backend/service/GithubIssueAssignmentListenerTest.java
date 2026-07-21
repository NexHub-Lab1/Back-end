package com.nexhub.backend.service;

import com.nexhub.backend.event.TaskAssignmentCreatedEvent;
import com.nexhub.backend.model.Task;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GithubIssueAssignmentListenerTest {
    @Test
    void automaticSyncAlwaysStartsInANewTransaction() throws NoSuchMethodException {
        Method method = GithubIssueService.class.getMethod("syncTaskIssue", Long.class);
        Transactional transactional = method.getAnnotation(Transactional.class);

        org.assertj.core.api.Assertions.assertThat(transactional).isNotNull();
        org.assertj.core.api.Assertions.assertThat(transactional.propagation())
                .isEqualTo(Propagation.REQUIRES_NEW);
    }

    @Test
    void githubFailureDoesNotEscapeAfterAssignmentCommit() {
        GithubIssueService service = mock(GithubIssueService.class);
        when(service.syncTaskIssue(20L)).thenThrow(new IllegalArgumentException("GitHub unavailable"));
        GithubIssueAssignmentListener listener = new GithubIssueAssignmentListener(service);

        assertThatCode(() -> listener.onAssignmentCreated(new TaskAssignmentCreatedEvent(20L)))
                .doesNotThrowAnyException();
        verify(service).syncTaskIssue(20L);
    }

    @Test
    void completedAutomaticSyncDoesNotThrow() {
        GithubIssueService service = mock(GithubIssueService.class);
        Task task = new Task();
        task.setGithubIssueSyncStatus("synced");
        task.setGithubIssueNumber(42);
        when(service.syncTaskIssue(20L)).thenReturn(task);
        GithubIssueAssignmentListener listener = new GithubIssueAssignmentListener(service);

        assertThatCode(() -> listener.onAssignmentCreated(new TaskAssignmentCreatedEvent(20L)))
                .doesNotThrowAnyException();
        verify(service).syncTaskIssue(20L);
    }
}
