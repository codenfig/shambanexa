package com.shambanexa.repository;

import com.shambanexa.entity.ProductionMonth;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ProductionMonthRepository extends JpaRepository<ProductionMonth, UUID> {
    List<ProductionMonth> findByTenantIdOrderByYearMonthAsc(UUID tenantId);
}
