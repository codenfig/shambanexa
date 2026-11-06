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
@Table(name = "staff_timesheets")
@Getter
@Setter
public class StaffTimesheet extends BaseEntity {
    @Column(nullable = false)
    private UUID tenantId;
    @Column(nullable = false)
    private UUID staffId;
    @Column(nullable = false)
    private UUID farmId;
    @Column(nullable = false)
    private LocalDate workDate;
    @Column(nullable = false, precision = 4, scale = 1)
    private BigDecimal hours;
    @Column(length = 400)
    private String note;
    @Column(nullable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
