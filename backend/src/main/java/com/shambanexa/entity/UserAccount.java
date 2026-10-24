package com.shambanexa.entity;

import com.shambanexa.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@Setter
public class UserAccount extends BaseEntity {

    @Column(nullable = false)
    private UUID tenantId;

    @Column(nullable = false, length = 180)
    private String email;

    @Column(nullable = false, length = 100)
    private String passwordHash;

    @Column(nullable = false, length = 160)
    private String fullName;

    @Column(nullable = false, length = 80)
    private String jobTitle;

    @Column(nullable = false, length = 40)
    private String status;

    @Column(nullable = false, length = 20)
    private String accessScope;

    @Column(nullable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
