package com.shambanexa.entity;

import com.shambanexa.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "tenants")
@Getter
@Setter
public class Tenant extends BaseEntity {

    @Column(nullable = false, length = 160)
    private String name;

    @Column(nullable = false, length = 40)
    private String code;

    @Column(nullable = false, length = 3)
    private String currencyCode;

    @Column(nullable = false, length = 64)
    private String timezone;

    @Column(nullable = false, length = 80)
    private String seasonLabel;

    @Column(nullable = false, length = 16)
    private String locale;

    @Column(nullable = false, length = 80)
    private String subscriptionPlan;

    @Column(nullable = false)
    private LocalDate subscriptionValidUntil;

    @Column(nullable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
