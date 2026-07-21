package com.nexhub.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.sql.Timestamp;
import java.time.Instant;

@Entity
@Table(name = "github_webhook_deliveries")
public class GithubWebhookDelivery {
    @Getter
    @Setter
    @Id
    @Column(name = "delivery_id", length = 100)
    private String deliveryId;

    @Getter
    @Setter
    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Getter
    @Setter
    @Column(name = "processed_at", nullable = false)
    private Timestamp processedAt;

    @PrePersist
    void prePersist() {
        if (processedAt == null) {
            processedAt = Timestamp.from(Instant.now());
        }
    }
}
