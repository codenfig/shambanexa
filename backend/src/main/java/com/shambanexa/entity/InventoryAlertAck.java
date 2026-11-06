package com.shambanexa.entity;

import com.shambanexa.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "inventory_alert_acks")
@Getter
@Setter
public class InventoryAlertAck extends BaseEntity {

    @Column(nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private UUID batchId;

    @Column(nullable = false, length = 40)
    private String alertKind;

    @Column(length = 160)
    private String assigneeName;

    @Column(length = 300)
    private String note;

    private Instant acknowledgedAt;

    private UUID acknowledgedBy;

    @Column(length = 160)
    private String acknowledgedByName;
}
