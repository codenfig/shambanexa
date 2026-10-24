package com.shambanexa.repository;

import com.shambanexa.entity.AdviserRating;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AdviserRatingRepository extends JpaRepository<AdviserRating, UUID> {
    List<AdviserRating> findByTenantId(UUID tenantId);

    Optional<AdviserRating> findByAdviserIdAndRaterId(UUID adviserId, UUID raterId);
}
