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
@Table(name = "medical_supplier")
@Getter
@Setter
public class MedicalSupplier extends BaseEntity {

    @Column(nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private UUID ownerId;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(nullable = false, length = 16)
    private String mark;

    @Column(nullable = false, length = 80)
    private String region;

    @Column(nullable = false, length = 80)
    private String district;

    @Column(nullable = false, length = 240)
    private String address;

    @Column(nullable = false, length = 40)
    private String phone;

    @Column(nullable = false, length = 120)
    private String openHours;

    @Column(nullable = false, precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(nullable = false, precision = 9, scale = 6)
    private BigDecimal longitude;

    @Column(nullable = false, length = 80)
    private String licenseNumber;

    @Column(length = 160)
    private String licenseAuthority;

    private java.time.LocalDate licenseExpiresOn;

    @Column(length = 400)
    private String productsOffered;

    @Column(nullable = false, length = 400)
    private String summary;

    @Column(nullable = false, length = 20)
    private String approval;

    @Column(nullable = false, length = 20)
    private String shopState;

    @Column(nullable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
