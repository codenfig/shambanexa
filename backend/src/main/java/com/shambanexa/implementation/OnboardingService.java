package com.shambanexa.implementation;

import com.shambanexa.common.AccessScopes;
import com.shambanexa.common.ApiException;
import com.shambanexa.common.CurrentUser;
import com.shambanexa.common.RolePermissions;
import com.shambanexa.common.TenantContext;
import com.shambanexa.dto.AuthResponse;
import com.shambanexa.dto.FarmRequest;
import com.shambanexa.dto.RegisterRequest;
import com.shambanexa.entity.Tenant;
import com.shambanexa.entity.UserAccount;
import com.shambanexa.repository.TenantRepository;
import com.shambanexa.repository.UserAccountRepository;
import com.shambanexa.service.AuthService;
import com.shambanexa.service.FarmService;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Service
public class OnboardingService {

    private final TenantRepository tenants;
    private final UserAccountRepository users;
    private final FarmService farms;
    private final AuthService auth;
    private final PasswordEncoder passwords;

    public OnboardingService(TenantRepository tenants, UserAccountRepository users, FarmService farms,
                             AuthService auth, PasswordEncoder passwords) {
        this.tenants = tenants;
        this.users = users;
        this.farms = farms;
        this.auth = auth;
        this.passwords = passwords;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase();
        if (users.findByEmailIgnoreCase(email).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "EMAIL_TAKEN", "An account with that email already exists");
        }
        Tenant tenant = tenants.findAll().stream().findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "TENANT_MISSING", "The workspace is not ready for registration"));
        UserAccount user = new UserAccount();
        user.setId(UUID.randomUUID());
        user.setTenantId(tenant.getId());
        user.setEmail(email);
        user.setPasswordHash(passwords.encode(request.password()));
        user.setFullName(request.fullName().trim());
        user.setJobTitle("Farm owner");
        user.setStatus("ACTIVE");
        user.setAccessScope(AccessScopes.OWN_FARM);
        users.saveAndFlush(user);

        Set<String> authorities = new LinkedHashSet<>();
        authorities.add("ROLE_" + RolePermissions.FARM_MANAGER);
        authorities.addAll(RolePermissions.permissions(RolePermissions.FARM_MANAGER));
        CurrentUser current = new CurrentUser(
                user.getId(),
                tenant.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getJobTitle(),
                RolePermissions.label(RolePermissions.FARM_MANAGER),
                false,
                Set.of(),
                authorities,
                AccessScopes.OWN_FARM,
                false,
                true,
                false
        );
        TenantContext.set(current);
        try {
            farms.create(new FarmRequest(
                    request.farmCode(),
                    request.farmName(),
                    request.region(),
                    request.latitude(),
                    request.longitude(),
                    request.address(),
                    request.fullName(),
                    request.contactPhone() == null ? "" : request.contactPhone(),
                    null,
                    request.boundary()
            ));
        } finally {
            TenantContext.clear();
        }
        return auth.login(email, request.password());
    }
}
