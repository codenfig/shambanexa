package com.shambanexa.entity;

import com.shambanexa.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "animals")
@Getter
@Setter
public class Animal extends BaseEntity {

    @Column(nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private UUID farmId;

    private UUID facilityId;

    @Column(nullable = false)
    private UUID speciesId;

    private UUID breedId;

    @Column(nullable = false, length = 40)
    private String tag;

    @Column(length = 120)
    private String name;

    @Column(nullable = false, length = 20)
    private String sex;

    private LocalDate birthDate;

    @Column(length = 80)
    private String color;

    @Column(nullable = false, length = 40)
    private String status;

    @Column(nullable = false, length = 40)
    private String healthStatus;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private Long version = 0L;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
        if (status == null) {
            status = "ACTIVE";
        }
        if (healthStatus == null) {
            healthStatus = "HEALTHY";
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
