package com.shambanexa.repository;

import com.shambanexa.entity.ActivityEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ActivityEventRepository extends JpaRepository<ActivityEvent, UUID> {
    List<ActivityEvent> findTop20ByTenantIdOrderByOccurredAtDesc(UUID tenantId);

    List<ActivityEvent> findByTenantIdOrderByOccurredAtDesc(UUID tenantId);
}
