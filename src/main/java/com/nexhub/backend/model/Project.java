package com.nexhub.backend.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
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
import java.sql.Date;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "projects")
public class Project {
    @Getter
    @jakarta.persistence.Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Getter
    @Setter
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @Getter
    @Setter
    private String name;

    @Getter
    @Setter
    private String description;

    @Getter
    @Setter
    private String githubRepo;

    @Getter
    @Setter
    private String status;

    @Getter
    @Setter
    private Date created_at;

    @Getter
    @Setter
    private Date updated_at;

    @Getter
    @Setter
    private Date last_active_at;

    @Getter
    @Setter
    private Long completed_tasks_count;

    @Getter
    @Setter
    private Long stars_count;

    @Getter
    @Setter
    @BatchSize(size = 20)
    @ManyToMany(cascade = {CascadeType.PERSIST, CascadeType.MERGE}, fetch = FetchType.LAZY)
    @JoinTable(
            name = "project_tags",
            joinColumns = @JoinColumn(name = "project_id"),
            inverseJoinColumns = @JoinColumn(name = "tag_id")
    )
    private Set<Tag> tags = new HashSet<>();

    @Getter
    @Setter
    @BatchSize(size = 20)
    @ManyToMany(cascade = {CascadeType.PERSIST, CascadeType.MERGE}, fetch = FetchType.LAZY)
    @JoinTable(
            name = "devs_project",
            joinColumns = @JoinColumn(name = "project_id"),
            inverseJoinColumns = @JoinColumn(name = "user_id")
    )
    private Set<User> contributors = new HashSet<>();

    @PrePersist
    void prePersist() {
        Date now = new Date(System.currentTimeMillis());
        if (created_at == null) {
            created_at = now;
        }
        if (updated_at == null) {
            updated_at = now;
        }
        if (last_active_at == null) {
            last_active_at = now;
        }
        if (status == null || status.isBlank()) {
            status = "active";
        }
        if (completed_tasks_count == null) {
            completed_tasks_count = 0L;
        }
        if (stars_count == null) {
            stars_count = 0L;
        }
    }

    @PreUpdate
    void preUpdate() {
        updated_at = new Date(System.currentTimeMillis());
    }
}
