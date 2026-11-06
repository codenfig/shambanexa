package com.shambanexa.repository;

import com.shambanexa.entity.StaffReview;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface StaffReviewRepository extends JpaRepository<StaffReview, UUID> {
    List<StaffReview> findByTenantIdOrderByReviewedOnDesc(UUID tenantId);
}
