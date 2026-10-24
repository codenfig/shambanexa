package com.shambanexa.implementation;

import com.shambanexa.service.AuthService;

import com.shambanexa.common.AccessScopes;
import com.shambanexa.common.ApiException;
import com.shambanexa.dto.AuthResponse;
import com.shambanexa.common.CurrentUser;
import com.shambanexa.common.RolePermissions;
import com.shambanexa.entity.UserAccount;
import com.shambanexa.entity.UserRoleGrant;
import com.shambanexa.repository.UserAccountRepository;
import com.shambanexa.repository.UserRoleGrantRepository;
import com.shambanexa.security.JwtService;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AuthServiceImpl implements AuthService {

    private final UserAccountRepository users;
    private final UserRoleGrantRepository grants;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthServiceImpl(UserAccountRepository users, UserRoleGrantRepository grants,
                       PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.users = users;
        this.grants = grants;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public AuthResponse login(String email, String password) {
        UserAccount user = users.findByEmailIgnoreCase(email.trim())
                .filter(candidate -> "ACTIVE".equals(candidate.getStatus()))
                .orElseThrow(this::invalid);
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw invalid();
        }
        CurrentUser current = assemble(user);
        return AuthResponse.from(current, jwtService.issue(user.getId()));
    }

    public CurrentUser load(UUID userId) {
        UserAccount user = users.findById(userId)
                .filter(candidate -> "ACTIVE".equals(candidate.getStatus()))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "AUTH_REQUIRED", "Sign in to continue"));
        return assemble(user);
    }

    public AuthResponse profile(CurrentUser current) {
        return AuthResponse.from(current, null);
    }

    private CurrentUser assemble(UserAccount user) {
        List<UserRoleGrant> roleGrants = grants.findByUserId(user.getId());
        String accessScope = user.getAccessScope() == null ? AccessScopes.SELECTED_FARMS : user.getAccessScope();
        boolean allFarms = AccessScopes.ALL_FARMS.equals(accessScope);
        Set<UUID> farmIds = allFarms
                ? Set.of()
                : roleGrants.stream()
                .map(UserRoleGrant::getFarmId)
                .filter(id -> id != null)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> authorities = new LinkedHashSet<>();
        String roleLabel = "User";
        List<String> ordered = roleGrants.stream()
                .map(UserRoleGrant::getRoleCode)
                .distinct()
                .sorted(Comparator.comparing(code -> RolePermissions.TENANT_ADMIN.equals(code) ? 0
                        : RolePermissions.FARM_EXPERT.equals(code) ? 2
                        : RolePermissions.AUDITOR.equals(code) ? 3 : 1))
                .toList();
        if (!ordered.isEmpty()) {
            roleLabel = RolePermissions.label(ordered.get(0));
        }
        for (String role : ordered) {
            authorities.add("ROLE_" + role);
            authorities.addAll(RolePermissions.permissions(role));
        }
        return new CurrentUser(
                user.getId(),
                user.getTenantId(),
                user.getEmail(),
                user.getFullName(),
                user.getJobTitle(),
                roleLabel,
                allFarms,
                farmIds,
                authorities,
                accessScope,
                authorities.contains("ROLE_" + RolePermissions.TENANT_ADMIN),
                authorities.contains("ANIMAL_WRITE"),
                authorities.contains("ADVICE_WRITE")
        );
    }

    private ApiException invalid() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Email or password is incorrect");
    }

    }
