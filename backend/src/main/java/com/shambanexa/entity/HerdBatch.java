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
import java.util.UUID;

@Entity
@Table(name = "batches")
@Getter
@Setter
public class HerdBatch extends BaseEntity {

    @Column(nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private UUID farmId;

    private UUID facilityId;

    @Column(nullable = false)
    private UUID speciesId;

    private UUID breedId;

    @Column(nullable = false, length = 40)
    private String code;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(nullable = false)
    private Integer quantity;

    @Column(nullable = false, length = 80)
    private String purpose;

    @Column(nullable = false, length = 40)
    private String status;

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
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
