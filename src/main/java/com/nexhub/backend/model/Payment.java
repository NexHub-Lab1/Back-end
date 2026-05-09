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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.sql.Date;

@Entity
@Table(name = "payments")
public class Payment {
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
    @JoinColumn(name = "payer_id", nullable = false)
    private User payer;

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
    private String provider;

    @Getter
    @Setter
    private String providerPaymentId;

    @Getter
    @Setter
    @Column(nullable = false, unique = true)
    private String externalReference;

    @Getter
    @Setter
    @Column(length = 500)
    private String checkoutUrl;

    @Getter
    @Setter
    @Column(nullable = false)
    private String status;

    @Getter
    @Setter
    @Column(columnDefinition = "TEXT")
    private String failureReason;

    @Getter
    @Setter
    @Column(nullable = false)
    private Date createdAt;

    @Getter
    @Setter
    @Column(nullable = false)
    private Date updatedAt;

    @Getter
    @Setter
    private Date approvedAt;

    @Getter
    @Setter
    private Date failedAt;

    @Getter
    @Setter
    private Date releasedAt;

    @Getter
    @Setter
    private Date refundedAt;

    @PrePersist
    void prePersist() {
        Date now = new Date(System.currentTimeMillis());
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
        if (status == null || status.isBlank()) {
            status = "pending";
        }
        if (provider == null || provider.isBlank()) {
            provider = "mercadopago";
        }
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = new Date(System.currentTimeMillis());
    }
}
