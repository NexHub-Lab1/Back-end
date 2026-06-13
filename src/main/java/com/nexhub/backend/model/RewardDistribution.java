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
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.sql.Date;

@Entity
@Table(
        name = "reward_distributions",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_reward_distribution_submission_recipient",
                columnNames = {"submission_id", "recipient_id"}
        )
)
public class RewardDistribution {
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
    @JoinColumn(name = "payment_id", nullable = false)
    private Payment payment;

    @Getter
    @Setter
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "submission_id", nullable = false)
    private TaskSubmission submission;

    @Getter
    @Setter
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "assignment_id", nullable = false)
    private TaskAssignment assignment;

    @Getter
    @Setter
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "recipient_id", nullable = false)
    private User recipient;

    @Getter
    @Setter
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Getter
    @Setter
    @Column(nullable = false, length = 10)
    private String currency;

    @Getter
    @Setter
    @Column(nullable = false)
    private String status;

    @Getter
    @Setter
    @Column(name = "created_at", nullable = false)
    private Date createdAt;

    @Getter
    @Setter
    @Column(name = "released_at")
    private Date releasedAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = new Date(System.currentTimeMillis());
        }
        if (status == null || status.isBlank()) {
            status = "pending";
        }
    }
}
