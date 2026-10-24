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
import java.util.UUID;

@Entity
@Table(name = "staff_members")
@Getter
@Setter
public class StaffMember extends BaseEntity {

    @Column(nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private UUID farmId;

    @Column(nullable = false, length = 160)
    private String fullName;

    @Column(nullable = false, length = 80)
    private String roleTitle;

    @Column(length = 40)
    private String phone;

    @Column(nullable = false, length = 40)
    private String status;

    private LocalDate hiredOn;

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
