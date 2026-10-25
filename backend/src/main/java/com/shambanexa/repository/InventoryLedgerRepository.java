package com.shambanexa.repository;

import com.shambanexa.entity.InventoryLedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface InventoryLedgerRepository extends JpaRepository<InventoryLedgerEntry, UUID> {
    List<InventoryLedgerEntry> findByTenantIdOrderByOccurredAtDesc(UUID tenantId);
}
