package com.nexhub.backend.service;

import com.nexhub.backend.event.TaskAssignmentCreatedEvent;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class GithubIssueAssignmentListener {
    private static final Logger log = LoggerFactory.getLogger(GithubIssueAssignmentListener.class);

    private final GithubIssueService githubIssueService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAssignmentCreated(TaskAssignmentCreatedEvent event) {
        try {
            githubIssueService.syncTaskIssue(event.taskId());
        } catch (RuntimeException e) {
            log.error("Unexpected GitHub issue synchronization failure for task {}", event.taskId(), e);
        }
    }
}
