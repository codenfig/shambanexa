package com.shambanexa.repository;

import com.shambanexa.entity.AdviserRecommendation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AdviserRecommendationRepository extends JpaRepository<AdviserRecommendation, UUID> {
    List<AdviserRecommendation> findByTenantId(UUID tenantId);

    Optional<AdviserRecommendation> findByAdviserIdAndRecommenderId(UUID adviserId, UUID recommenderId);
}
