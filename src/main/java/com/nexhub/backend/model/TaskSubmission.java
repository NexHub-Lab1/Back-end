package com.nexhub.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.sql.Date;

@Entity
@Table(name = "task_submissions")
public class TaskSubmission {
    @Getter
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Getter
    @Setter
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "task_id", nullable = false)
    private Task task;

    @Getter
    @Setter
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "assignment_id", nullable = false)
    private TaskAssignment assignment;

    @Getter
    @Setter
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Getter
    @Setter
    @Column(name = "pull_request_url", nullable = false, length = 500)
    private String pullRequestUrl;

    @Getter
    @Setter
    @Column(name = "submitted_at", nullable = false)
    private Date submittedAt;

    @Getter
    @Setter
    @Column(nullable = false)
    private String status;

    @Getter
    @Setter
    @Column(name = "review_comments", columnDefinition = "TEXT")
    private String reviewComments;

    @Getter
    @Setter
    @Column(name = "reviewed_at")
    private Date reviewedAt;

    @Getter
    @Setter
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "reviewer_id")
    private User reviewer;

    @Getter
    @Setter
    @Column(name = "attempts_used", nullable = false)
    private Integer attemptsUsed;

    @PrePersist
    void prePersist() {
        if (submittedAt == null) {
            submittedAt = new Date(System.currentTimeMillis());
        }
        if (status == null || status.isBlank()) {
            status = "submitted";
        }
        if (attemptsUsed == null || attemptsUsed < 1) {
            attemptsUsed = 1;
        }
    }
}
