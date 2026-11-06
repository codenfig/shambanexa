package com.shambanexa.repository;

import com.shambanexa.entity.MedicineRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MedicineRequestRepository extends JpaRepository<MedicineRequest, UUID> {
    List<MedicineRequest> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    Optional<MedicineRequest> findByTenantIdAndId(UUID tenantId, UUID id);
}
