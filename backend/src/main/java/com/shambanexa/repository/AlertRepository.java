package com.shambanexa.repository;

import com.shambanexa.entity.Alert;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AlertRepository extends JpaRepository<Alert, UUID> {
    List<Alert> findByTenantIdAndStatusOrderByOccurredAtDesc(UUID tenantId, String status);
}
