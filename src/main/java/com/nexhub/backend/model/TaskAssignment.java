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
@Table(name = "task_assignments")
public class TaskAssignment {
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
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Getter
    @Setter
    @Column(name = "assigned_at", nullable = false)
    private Date assignedAt;

    @Getter
    @Setter
    @Column(nullable = false)
    private String status;

    @Getter
    @Setter
    @Column(name = "attempts_used", nullable = false)
    private Integer attemptsUsed;

    @PrePersist
    void prePersist() {
        if (assignedAt == null) {
            assignedAt = new Date(System.currentTimeMillis());
        }
        if (status == null || status.isBlank()) {
            status = "active";
        }
        if (attemptsUsed == null || attemptsUsed < 0) {
            attemptsUsed = 0;
        }
    }
}
