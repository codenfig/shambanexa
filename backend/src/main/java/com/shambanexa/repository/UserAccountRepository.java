package com.shambanexa.repository;

import com.shambanexa.entity.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {
    Optional<UserAccount> findByEmailIgnoreCase(String email);

    List<UserAccount> findByTenantIdOrderByFullNameAsc(UUID tenantId);
}
