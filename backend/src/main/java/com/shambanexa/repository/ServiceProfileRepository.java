package com.shambanexa.repository;

import com.shambanexa.entity.ServiceProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ServiceProfileRepository extends JpaRepository<ServiceProfile, UUID> {
}
