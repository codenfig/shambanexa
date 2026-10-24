package com.shambanexa.repository;

import com.shambanexa.entity.HealthCase;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface HealthCaseRepository extends JpaRepository<HealthCase, UUID> {
    List<HealthCase> findByTenantIdOrderByOpenedOnDesc(UUID tenantId);
}
