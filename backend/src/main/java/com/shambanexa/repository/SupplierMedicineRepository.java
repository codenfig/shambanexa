package com.shambanexa.repository;

import com.shambanexa.entity.SupplierMedicine;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SupplierMedicineRepository extends JpaRepository<SupplierMedicine, UUID> {
    List<SupplierMedicine> findByTenantIdOrderByNameAsc(UUID tenantId);

    List<SupplierMedicine> findByTenantIdAndSupplierIdOrderByNameAsc(UUID tenantId, UUID supplierId);

    Optional<SupplierMedicine> findByTenantIdAndId(UUID tenantId, UUID id);
}
