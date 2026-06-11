package com.nexhub.backend.service;

import com.nexhub.backend.model.TaskAssignment;
import com.nexhub.backend.repository.TaskAssignmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TaskExpirationScheduler {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(TaskExpirationScheduler.class);

    private final TaskAssignmentRepository taskAssignmentRepository;
    private final NotificationService notificationService;

    // Run every 5 minutes
    @Scheduled(fixedDelay = 300000)
    @Transactional
    public void expireOverdueAssignments() {
        log.info("Running task deadline expiration check...");
        Date now = new Date(System.currentTimeMillis());
        List<TaskAssignment> overdue = taskAssignmentRepository.findActiveOverdueAssignments(now);
        
        if (overdue.isEmpty()) {
            return;
        }

        log.info("Found {} overdue assignments to expire", overdue.size());
        for (TaskAssignment assignment : overdue) {
            assignment.setStatus("failed");
            taskAssignmentRepository.save(assignment);

            notificationService.sendNotification(
                    assignment.getUser(),
                    "Assignment expired: You missed the deadline for task: " + assignment.getTask().getTitle(),
                    "WARNING",
                    "/task/" + assignment.getTask().getId()
            );
        }
    }
}
