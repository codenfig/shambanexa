package com.shambanexa.entity;

import com.shambanexa.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "staff_shifts")
@Getter
@Setter
public class StaffShift extends BaseEntity {
    @Column(nullable = false)
    private UUID tenantId;
    @Column(nullable = false)
    private UUID staffId;
    @Column(nullable = false)
    private UUID farmId;
    @Column(nullable = false)
    private short weekday;
    @Column(nullable = false)
    private LocalTime starts;
    @Column(nullable = false)
    private LocalTime ends;
}
