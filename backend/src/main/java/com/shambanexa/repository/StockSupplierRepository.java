package com.shambanexa.repository;

import com.shambanexa.entity.StockSupplier;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StockSupplierRepository extends JpaRepository<StockSupplier, UUID> {
    List<StockSupplier> findByTenantIdOrderByNameAsc(UUID tenantId);

    Optional<StockSupplier> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<StockSupplier> findByTenantIdAndNameIgnoreCase(UUID tenantId, String name);
}
