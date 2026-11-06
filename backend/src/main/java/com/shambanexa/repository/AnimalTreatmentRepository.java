package com.shambanexa.repository;

import com.shambanexa.entity.AnimalTreatment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AnimalTreatmentRepository extends JpaRepository<AnimalTreatment, UUID> {
    List<AnimalTreatment> findByTenantIdOrderByStartedOnDesc(UUID tenantId);

    Optional<AnimalTreatment> findByIdAndTenantId(UUID id, UUID tenantId);
}
