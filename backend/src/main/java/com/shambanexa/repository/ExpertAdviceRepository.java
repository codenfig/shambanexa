package com.shambanexa.repository;

import com.shambanexa.entity.ExpertAdvice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ExpertAdviceRepository extends JpaRepository<ExpertAdvice, UUID> {
    List<ExpertAdvice> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
}
