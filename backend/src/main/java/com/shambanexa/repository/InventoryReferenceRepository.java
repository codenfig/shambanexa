package com.shambanexa.repository;

import com.shambanexa.entity.InventoryReference;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InventoryReferenceRepository extends JpaRepository<InventoryReference, UUID> {
    List<InventoryReference> findByTenantIdOrderBySortOrderAscNameAsc(UUID tenantId);

    List<InventoryReference> findByTenantIdAndKindOrderBySortOrderAscNameAsc(UUID tenantId, String kind);

    Optional<InventoryReference> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<InventoryReference> findByTenantIdAndKindAndCodeIgnoreCase(UUID tenantId, String kind, String code);

    long countByTenantIdAndParentId(UUID tenantId, UUID parentId);
}
