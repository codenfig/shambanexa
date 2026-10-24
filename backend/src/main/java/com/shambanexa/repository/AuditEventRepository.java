package com.shambanexa.repository;

import com.shambanexa.entity.AuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {
    List<AuditEvent> findTop40ByTenantIdOrderByRecordedAtDesc(UUID tenantId);
}
