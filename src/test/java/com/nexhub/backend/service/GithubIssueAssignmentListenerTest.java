package com.nexhub.backend.service;

import com.nexhub.backend.event.TaskAssignmentCreatedEvent;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GithubIssueAssignmentListenerTest {
    @Test
    void githubFailureDoesNotEscapeAfterAssignmentCommit() {
        GithubIssueService service = mock(GithubIssueService.class);
        when(service.syncTaskIssue(20L)).thenThrow(new IllegalArgumentException("GitHub unavailable"));
        GithubIssueAssignmentListener listener = new GithubIssueAssignmentListener(service);

        assertThatCode(() -> listener.onAssignmentCreated(new TaskAssignmentCreatedEvent(20L)))
                .doesNotThrowAnyException();
        verify(service).syncTaskIssue(20L);
    }
}
