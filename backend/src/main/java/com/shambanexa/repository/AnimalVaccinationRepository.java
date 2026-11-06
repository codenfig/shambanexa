package com.shambanexa.repository;

import com.shambanexa.entity.AnimalVaccination;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AnimalVaccinationRepository extends JpaRepository<AnimalVaccination, UUID> {
    List<AnimalVaccination> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    Optional<AnimalVaccination> findByIdAndTenantId(UUID id, UUID tenantId);
}
