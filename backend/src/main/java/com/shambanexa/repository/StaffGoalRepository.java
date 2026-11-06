package com.shambanexa.repository;

import com.shambanexa.entity.StaffGoal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface StaffGoalRepository extends JpaRepository<StaffGoal, UUID> {
    List<StaffGoal> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
}
