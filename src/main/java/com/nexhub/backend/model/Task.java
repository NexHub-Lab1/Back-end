package com.nexhub.backend.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import org.hibernate.annotations.BatchSize;
import java.math.BigDecimal;
import java.sql.Date;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "tasks")
public class Task {
    @Getter
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Getter
    @Setter
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Getter
    @Setter
    @Column(nullable = false)
    private String title;

    @Getter
    @Setter
    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Getter
    @Setter
    @Column(columnDefinition = "TEXT")
    private String deliverables;

    @Getter
    @Setter
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal rewardAmount;

    @Getter
    @Setter
    @Column(nullable = false, length = 10)
    private String rewardCurrency;

    @Getter
    @Setter
    private Date deadline;

    @Getter
    @Setter
    @Column(nullable = false)
    private String status;

    @Getter
    @Setter
    @Column(name = "funding_status")
    private String fundingStatus;

    @Getter
    @Setter
    @Column(nullable = false)
    private Integer maxAttempts;

    @Getter
    @Setter
    @Column(nullable = false)
    private Date created_at;

    @Getter
    @Setter
    @Column(nullable = false)
    private Date updated_at;

    @Getter
    @Setter
    @BatchSize(size = 20)
    @ManyToMany(cascade = {CascadeType.PERSIST, CascadeType.MERGE}, fetch = FetchType.LAZY)
    @JoinTable(
            name = "task_skills",
            joinColumns = @JoinColumn(name = "task_id"),
            inverseJoinColumns = @JoinColumn(name = "tag_id")
    )
    private Set<Tag> recommendedSkills = new HashSet<>();

    @PrePersist
    void prePersist() {
        Date now = new Date(System.currentTimeMillis());
        if (created_at == null) {
            created_at = now;
        }
        if (updated_at == null) {
            updated_at = now;
        }
        if (status == null || status.isBlank()) {
            status = "open";
        }
        if (fundingStatus == null || fundingStatus.isBlank()) {
            fundingStatus = "unfunded";
        }
        if (rewardCurrency == null || rewardCurrency.isBlank()) {
            rewardCurrency = "ARS";
        }
        if (maxAttempts == null || maxAttempts < 1) {
            maxAttempts = 1;
        }
    }

    @PreUpdate
    void preUpdate() {
        updated_at = new Date(System.currentTimeMillis());
    }
}
