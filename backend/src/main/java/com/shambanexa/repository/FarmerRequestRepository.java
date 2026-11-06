package com.shambanexa.repository;

import com.shambanexa.entity.FarmerRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface FarmerRequestRepository extends JpaRepository<FarmerRequest, UUID> {
    List<FarmerRequest> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
}
