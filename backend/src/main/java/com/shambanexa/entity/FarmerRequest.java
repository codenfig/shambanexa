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
@Table(name = "farmer_requests")
@Getter
@Setter
public class FarmerRequest extends BaseEntity {
    @Column(nullable = false)
    private UUID tenantId;
    @Column(nullable = false)
    private UUID farmId;
    @Column(nullable = false)
    private UUID authorUserId;
    @Column(nullable = false, length = 160)
    private String authorName;
    @Column(nullable = false, length = 160)
    private String subject;
    @Column(nullable = false, length = 2000)
    private String message;
    @Column(nullable = false, length = 20)
    private String status;
    @Column(length = 2000)
    private String reply;
    @Column(length = 160)
    private String repliedBy;
    @Column(nullable = false)
    private Instant createdAt;
    private Instant answeredAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
        if (status == null) status = "OPEN";
    }
}
