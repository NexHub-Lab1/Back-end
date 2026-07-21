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

import java.sql.Timestamp;
import java.time.Instant;

@Entity
@Table(
        name = "github_pull_request_comments",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_github_pr_comment_type_id",
                columnNames = {"event_type", "github_comment_id"}
        )
)
public class GithubPullRequestComment {
    @Getter
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Getter
    @Setter
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "submission_id", nullable = false)
    private TaskSubmission submission;

    @Getter
    @Setter
    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Getter
    @Setter
    @Column(name = "github_comment_id", nullable = false)
    private Long githubCommentId;

    @Getter
    @Setter
    @Column(name = "last_delivery_id", length = 100)
    private String lastDeliveryId;

    @Getter
    @Setter
    @Column(name = "author_username", length = 100)
    private String authorUsername;

    @Getter
    @Setter
    @Column(name = "author_avatar_url", length = 1000)
    private String authorAvatarUrl;

    @Getter
    @Setter
    @Column(columnDefinition = "TEXT")
    private String body;

    @Getter
    @Setter
    @Column(name = "github_url", length = 1000)
    private String githubUrl;

    @Getter
    @Setter
    @Column(name = "github_created_at")
    private Timestamp githubCreatedAt;

    @Getter
    @Setter
    @Column(name = "github_updated_at")
    private Timestamp githubUpdatedAt;

    @Getter
    @Setter
    @Column(nullable = false)
    private Boolean deleted = false;

    @Getter
    @Setter
    @Column(name = "received_at", nullable = false)
    private Timestamp receivedAt;

    @PrePersist
    void prePersist() {
        if (deleted == null) {
            deleted = false;
        }
        if (receivedAt == null) {
            receivedAt = Timestamp.from(Instant.now());
        }
    }
}
