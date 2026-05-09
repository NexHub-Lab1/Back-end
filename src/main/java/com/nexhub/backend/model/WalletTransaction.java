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

import java.math.BigDecimal;
import java.sql.Date;

@Entity
@Table(name = "wallet_transactions")
public class WalletTransaction {
    @Getter
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Getter
    @Setter
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Getter
    @Setter
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "payment_id")
    private Payment payment;

    @Getter
    @Setter
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "task_id")
    private Task task;

    @Getter
    @Setter
    @Column(nullable = false)
    private String type;

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
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal availableBalanceAfter;

    @Getter
    @Setter
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal escrowBalanceAfter;

    @Getter
    @Setter
    @Column(columnDefinition = "TEXT")
    private String description;

    @Getter
    @Setter
    @Column(nullable = false)
    private Date createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = new Date(System.currentTimeMillis());
        }
    }
}
