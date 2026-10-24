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
@Table(name = "adviser_recommendations")
@Getter
@Setter
public class AdviserRecommendation extends BaseEntity {

    @Column(nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private UUID adviserId;

    @Column(nullable = false)
    private UUID recommenderId;

    @Column(length = 400)
    private String note;

    @Column(nullable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
