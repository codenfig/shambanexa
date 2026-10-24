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
@Table(name = "expert_advice")
@Getter
@Setter
public class ExpertAdvice extends BaseEntity {

    @Column(nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private UUID authorId;

    @Column(nullable = false, length = 160)
    private String topic;

    @Column(nullable = false, length = 2000)
    private String advice;

    @Column(length = 160)
    private String medication;

    @Column(length = 400)
    private String medicationNote;

    @Column(nullable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
