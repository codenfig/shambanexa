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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "farms")
@Getter
@Setter
public class Farm extends BaseEntity {

    @Column(nullable = false)
    private UUID tenantId;

    @Column(nullable = false, length = 40)
    private String code;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(nullable = false, length = 80)
    private String region;

    @Column(nullable = false, length = 40)
    private String status;

    @Column(nullable = false, precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(nullable = false, precision = 9, scale = 6)
    private BigDecimal longitude;

    @Column(nullable = false, length = 64)
    private String timezone;

    @Column(nullable = false, length = 300)
    private String address;

    @Column(length = 160)
    private String managerName;

    @Column(length = 40)
    private String contactPhone;

    private LocalDate establishedOn;

    @Column(length = 8000)
    private String boundary;

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
        if (timezone == null) {
            timezone = "Africa/Dar_es_Salaam";
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
