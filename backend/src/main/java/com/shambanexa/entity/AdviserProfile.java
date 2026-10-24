package com.shambanexa.entity;

import com.shambanexa.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "adviser_profiles")
@Getter
@Setter
public class AdviserProfile extends BaseEntity {

    @Column(nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 500)
    private String talents;

    @Column(nullable = false, length = 20)
    private String availability;

    @Column(length = 240)
    private String availabilityNote;

    @Column(nullable = false)
    private Instant updatedAt;

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
        if (talents == null) {
            talents = "";
        }
    }
}
