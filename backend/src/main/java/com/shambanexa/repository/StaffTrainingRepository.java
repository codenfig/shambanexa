package com.shambanexa.repository;

import com.shambanexa.entity.StaffTraining;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface StaffTrainingRepository extends JpaRepository<StaffTraining, UUID> {
    List<StaffTraining> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
}
