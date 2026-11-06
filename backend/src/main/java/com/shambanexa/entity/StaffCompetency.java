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
@Table(name = "staff_competencies")
@Getter
@Setter
public class StaffCompetency extends BaseEntity {
    @Column(nullable = false)
    private UUID tenantId;
    @Column(nullable = false)
    private UUID staffId;
    @Column(nullable = false)
    private UUID farmId;
    @Column(nullable = false, length = 80)
    private String skill;
    @Column(nullable = false, length = 20)
    private String level;
    @Column(nullable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
