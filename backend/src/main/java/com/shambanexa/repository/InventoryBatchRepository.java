package com.shambanexa.repository;

import com.shambanexa.entity.InventoryBatch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InventoryBatchRepository extends JpaRepository<InventoryBatch, UUID> {
    List<InventoryBatch> findByTenantIdOrderByExpiresOnAsc(UUID tenantId);

    List<InventoryBatch> findByTenantIdAndItemIdOrderByExpiresOnAsc(UUID tenantId, UUID itemId);

    Optional<InventoryBatch> findByIdAndTenantId(UUID id, UUID tenantId);

    long countByTenantIdAndItemId(UUID tenantId, UUID itemId);
}
