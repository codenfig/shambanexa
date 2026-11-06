package com.shambanexa.repository;

import com.shambanexa.entity.TreatmentDose;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TreatmentDoseRepository extends JpaRepository<TreatmentDose, UUID> {
    List<TreatmentDose> findByTenantId(UUID tenantId);

    List<TreatmentDose> findByTreatmentId(UUID treatmentId);
}
