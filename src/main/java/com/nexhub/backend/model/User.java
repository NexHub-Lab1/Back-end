package com.nexhub.backend.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.sql.Date;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "users")
public class User {
    @Getter
    @jakarta.persistence.Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Getter
    @Setter
    private String username;

    @Getter
    @Setter
    private String email;

    @Getter
    @Setter
    private String password;

    @Getter
    @Setter
    private Integer github_id;

    @Getter
    @Setter
    private String bio;

    @Getter
    @Setter
    private String github_username;

    @Getter
    @Setter
    private String github_access_token;

    @Getter
    @Setter
    private String profile_image_url;

    @Getter
    @Setter
    private String status;

    @Getter
    @Setter
    private Integer total_points;

    @Getter
    @Setter
    private Integer streak_day;

    @Getter
    @Setter
    private Integer reputation_score;

    @Getter
    @Setter
    @Column(name = "available_balance", precision = 12, scale = 2)
    private BigDecimal availableBalance;

    @Getter
    @Setter
    @Column(name = "escrow_balance", precision = 12, scale = 2)
    private BigDecimal escrowBalance;

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
    @OneToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "followed_id",
            joinColumns = @JoinColumn(name = "from_id"),
            inverseJoinColumns = @JoinColumn(name = "to_id")
    )
    private Set<User> follows;

    @Getter
    @Setter
    @ManyToMany(cascade = {CascadeType.PERSIST, CascadeType.MERGE}, fetch = FetchType.EAGER)
    @JoinTable(
            name = "user_skills",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "tag_id")
    )
    private Set<Tag> skills = new HashSet<>();

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
        if (availableBalance == null) {
            availableBalance = BigDecimal.ZERO;
        }
        if (escrowBalance == null) {
            escrowBalance = BigDecimal.ZERO;
        }
    }

    @PreUpdate
    void preUpdate() {
        updated_at = new Date(System.currentTimeMillis());
        if (availableBalance == null) {
            availableBalance = BigDecimal.ZERO;
        }
        if (escrowBalance == null) {
            escrowBalance = BigDecimal.ZERO;
        }
    }
}
