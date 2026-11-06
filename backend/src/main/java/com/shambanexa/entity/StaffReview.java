package com.shambanexa.entity;

import com.shambanexa.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "staff_reviews")
@Getter
@Setter
public class StaffReview extends BaseEntity {
    @Column(nullable = false)
    private UUID tenantId;
    @Column(nullable = false)
    private UUID staffId;
    @Column(nullable = false)
    private UUID farmId;
    @Column(nullable = false, precision = 2, scale = 1)
    private BigDecimal score;
    @Column(nullable = false)
    private LocalDate reviewedOn;
    @Column(length = 1000)
    private String notes;
    @Column(nullable = false, length = 160)
    private String reviewerName;
    @Column(nullable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
