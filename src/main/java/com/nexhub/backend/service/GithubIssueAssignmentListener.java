package com.nexhub.backend.service;

import com.nexhub.backend.event.TaskAssignmentCreatedEvent;
import com.nexhub.backend.model.Task;
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
            Task task = githubIssueService.syncTaskIssue(event.taskId());
            if ("failed".equalsIgnoreCase(task.getGithubIssueSyncStatus())) {
                log.warn("Automatic GitHub issue synchronization failed for task {}: {}",
                        event.taskId(), task.getGithubIssueLastError());
            } else {
                log.info("Automatic GitHub issue synchronization completed for task {} with issue #{}",
                        event.taskId(), task.getGithubIssueNumber());
            }
        } catch (RuntimeException e) {
            log.error("Unexpected GitHub issue synchronization failure for task {}", event.taskId(), e);
        }
    }
}
