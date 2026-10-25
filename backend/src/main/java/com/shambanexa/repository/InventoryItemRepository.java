package com.shambanexa.repository;

import com.shambanexa.entity.InventoryItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InventoryItemRepository extends JpaRepository<InventoryItem, UUID> {
    List<InventoryItem> findByTenantIdOrderByNameAsc(UUID tenantId);

    Optional<InventoryItem> findByIdAndTenantId(UUID id, UUID tenantId);

    boolean existsByTenantIdAndFarmIdAndSkuIgnoreCase(UUID tenantId, UUID farmId, String sku);

    Optional<InventoryItem> findByTenantIdAndFarmIdAndSkuIgnoreCase(UUID tenantId, UUID farmId, String sku);

    List<InventoryItem> findTop8ByTenantIdAndNameContainingIgnoreCase(UUID tenantId, String name);
}
