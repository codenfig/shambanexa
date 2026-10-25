package com.shambanexa.repository;

import com.shambanexa.entity.InventoryAdjustment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InventoryAdjustmentRepository extends JpaRepository<InventoryAdjustment, UUID> {
    List<InventoryAdjustment> findByTenantIdOrderByRequestedAtDesc(UUID tenantId);

    Optional<InventoryAdjustment> findByIdAndTenantId(UUID id, UUID tenantId);
}
