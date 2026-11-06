package com.shambanexa.repository;

import com.shambanexa.entity.VaccinationProtocol;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VaccinationProtocolRepository extends JpaRepository<VaccinationProtocol, UUID> {
    List<VaccinationProtocol> findByTenantIdOrderByNameAsc(UUID tenantId);

    Optional<VaccinationProtocol> findByIdAndTenantId(UUID id, UUID tenantId);
}
