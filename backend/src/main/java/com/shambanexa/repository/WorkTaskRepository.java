package com.shambanexa.repository;

import com.shambanexa.entity.WorkTask;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface WorkTaskRepository extends JpaRepository<WorkTask, UUID> {
    List<WorkTask> findByTenantIdOrderByDueOnAsc(UUID tenantId);
}
