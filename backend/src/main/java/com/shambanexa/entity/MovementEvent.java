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
@Table(name = "movement_events")
@Getter
@Setter
public class MovementEvent extends BaseEntity {

    @Column(nullable = false)
    private UUID tenantId;

    @Column(nullable = false, length = 40)
    private String eventType;

    @Column(nullable = false, length = 40)
    private String subjectType;

    @Column(nullable = false)
    private UUID subjectId;

    private UUID farmId;

    private UUID sourceFarmId;

    private UUID destinationFarmId;

    @Column(nullable = false)
    private Integer quantity;

    @Column(nullable = false)
    private Instant effectiveAt;

    @Column(nullable = false)
    private Instant recordedAt;

    private UUID actorId;

    @Column(length = 300)
    private String reason;

    @Column(nullable = false, length = 80)
    private String idempotencyKey;

    @PrePersist
    void onCreate() {
        if (recordedAt == null) {
            recordedAt = Instant.now();
        }
        if (effectiveAt == null) {
            effectiveAt = recordedAt;
        }
    }
}
