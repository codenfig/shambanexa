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
import java.util.UUID;

@Entity
@Table(name = "facilities")
@Getter
@Setter
public class Facility extends BaseEntity {

    @Column(nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private UUID farmId;

    private UUID parentId;

    @Column(nullable = false, length = 40)
    private String code;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(nullable = false, length = 40)
    private String facilityType;

    @Column(nullable = false, length = 40)
    private String status;

    @Column(precision = 14, scale = 2)
    private BigDecimal capacity;

    @Column(length = 40)
    private String capacityUnit;

    @Column(length = 160)
    private String supervisorName;

    @Column(nullable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (status == null) {
            status = "ACTIVE";
        }
    }
}
