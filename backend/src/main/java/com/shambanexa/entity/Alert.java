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
@Table(name = "alerts")
@Getter
@Setter
public class Alert extends BaseEntity {

    @Column(nullable = false)
    private UUID tenantId;

    private UUID farmId;

    @Column(nullable = false, length = 20)
    private String severity;

    @Column(nullable = false, length = 160)
    private String title;

    @Column(nullable = false, length = 400)
    private String body;

    @Column(nullable = false, length = 40)
    private String status;

    @Column(length = 40)
    private String sourceType;

    private UUID sourceId;

    @Column(nullable = false)
    private Instant occurredAt;

    @Column(nullable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (occurredAt == null) {
            occurredAt = now;
        }
        if (status == null) {
            status = "OPEN";
        }
    }
}
