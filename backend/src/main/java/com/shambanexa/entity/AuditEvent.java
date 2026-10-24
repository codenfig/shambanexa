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
@Table(name = "audit_events")
@Getter
@Setter
public class AuditEvent extends BaseEntity {

    @Column(nullable = false)
    private UUID tenantId;

    private UUID actorId;

    @Column(nullable = false, length = 80)
    private String action;

    @Column(nullable = false, length = 80)
    private String entityType;

    private UUID entityId;

    @Column(length = 500)
    private String detail;

    @Column(nullable = false)
    private Instant recordedAt;

    @PrePersist
    void onCreate() {
        if (recordedAt == null) {
            recordedAt = Instant.now();
        }
    }
}
