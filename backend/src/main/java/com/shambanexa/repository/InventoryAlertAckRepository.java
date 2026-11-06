package com.shambanexa.repository;

import com.shambanexa.entity.InventoryAlertAck;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InventoryAlertAckRepository extends JpaRepository<InventoryAlertAck, UUID> {
    List<InventoryAlertAck> findByTenantId(UUID tenantId);

    Optional<InventoryAlertAck> findByTenantIdAndBatchIdAndAlertKind(UUID tenantId, UUID batchId, String alertKind);
}
