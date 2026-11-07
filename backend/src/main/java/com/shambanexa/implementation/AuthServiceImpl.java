package com.shambanexa.implementation;

import com.shambanexa.service.AuditTrail;
import com.shambanexa.service.AuthService;

import com.shambanexa.common.AccessScopes;
import com.shambanexa.common.ApiException;
import com.shambanexa.dto.AccountPhoto;
import com.shambanexa.dto.AuthResponse;
import com.shambanexa.common.CurrentUser;
import com.shambanexa.common.RolePermissions;
import com.shambanexa.entity.AccessRoleDefinition;
import com.shambanexa.entity.AccessRolePermission;
import com.shambanexa.entity.UserAccount;
import com.shambanexa.entity.UserRoleGrant;
import com.shambanexa.repository.AccessRoleDefinitionRepository;
import com.shambanexa.repository.AccessRolePermissionRepository;
import com.shambanexa.repository.UserAccountRepository;
import com.shambanexa.repository.UserRoleGrantRepository;
import com.shambanexa.security.JwtService;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AuthServiceImpl implements AuthService {

    private final UserAccountRepository users;
    private final UserRoleGrantRepository grants;
    private final AccessRoleDefinitionRepository roleDefinitions;
    private final AccessRolePermissionRepository rolePermissions;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuditTrail auditTrail;

    private static final int PHOTO_LIMIT = 2_000_000;

    public AuthServiceImpl(UserAccountRepository users, UserRoleGrantRepository grants,
                           AccessRoleDefinitionRepository roleDefinitions, AccessRolePermissionRepository rolePermissions,
                           PasswordEncoder passwordEncoder, JwtService jwtService, AuditTrail auditTrail) {
        this.users = users;
        this.grants = grants;
        this.roleDefinitions = roleDefinitions;
        this.rolePermissions = rolePermissions;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.auditTrail = auditTrail;
    }

    public AuthResponse login(String email, String password) {
        UserAccount user = users.findByEmailIgnoreCase(email.trim())
                .filter(candidate -> "ACTIVE".equals(candidate.getStatus()))
                .orElseThrow(this::invalid);
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw invalid();
        }
        CurrentUser current = assemble(user);
        return AuthResponse.from(current, jwtService.issue(user.getId()), user.getPhone(), hasPhoto(user));
    }

    public CurrentUser load(UUID userId) {
        UserAccount user = users.findById(userId)
                .filter(candidate -> "ACTIVE".equals(candidate.getStatus()))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "AUTH_REQUIRED", "Sign in to continue"));
        return assemble(user);
    }

    public AuthResponse profile(CurrentUser current) {
        UserAccount user = users.findById(current.id()).orElseThrow(this::invalid);
        return AuthResponse.from(current, null, user.getPhone(), hasPhoto(user));
    }

    public AuthResponse updatePhone(CurrentUser current, String phone) {
        UserAccount user = requireUser(current.id());
        String next = phone == null ? "" : phone.trim();
        if (next.length() > 40) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PHONE_INVALID", "Use a phone number of 40 characters or fewer.");
        }
        if (!next.isEmpty() && !next.matches("[0-9+() .\\-]{6,40}")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PHONE_INVALID", "Enter a phone number using digits, spaces, and + - ( ).");
        }
        user.setPhone(next.isEmpty() ? null : next);
        users.save(user);
        auditTrail.record("PROFILE_UPDATED", "USER", user.getId(), "Phone number updated");
        return profile(current);
    }

    public void changePassword(CurrentUser current, String currentPassword, String newPassword) {
        UserAccount user = requireUser(current.id());
        if (currentPassword == null || !passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PASSWORD_INCORRECT", "Current password is incorrect.");
        }
        if (newPassword == null || newPassword.length() < 8 || newPassword.length() > 80) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PASSWORD_INVALID", "Use a new password of 8 to 80 characters.");
        }
        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PASSWORD_UNCHANGED", "Choose a new password that is different from the current one.");
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        users.save(user);
        auditTrail.record("PASSWORD_CHANGED", "USER", user.getId(), "Password changed");
    }

    public AuthResponse savePhoto(CurrentUser current, byte[] bytes, String contentType) {
        UserAccount user = requireUser(current.id());
        if (bytes == null || bytes.length == 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PHOTO_REQUIRED", "Choose an image to add.");
        }
        if (bytes.length > PHOTO_LIMIT) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PHOTO_TOO_LARGE", "Use an image of 2 MB or smaller.");
        }
        String type = contentType == null ? "" : contentType.toLowerCase();
        if (!type.equals("image/jpeg") && !type.equals("image/png") && !type.equals("image/webp")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PHOTO_TYPE", "Use a JPEG, PNG, or WebP image.");
        }
        user.setPhoto(bytes);
        user.setPhotoType(type);
        users.save(user);
        auditTrail.record("PROFILE_UPDATED", "USER", user.getId(), "Profile image updated");
        return profile(current);
    }

    public AccountPhoto photo(CurrentUser current) {
        UserAccount user = requireUser(current.id());
        if (!hasPhoto(user)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "PHOTO_MISSING", "No profile image is stored.");
        }
        return new AccountPhoto(user.getPhoto(), user.getPhotoType());
    }

    private UserAccount requireUser(UUID userId) {
        return users.findById(userId)
                .filter(candidate -> "ACTIVE".equals(candidate.getStatus()))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "AUTH_REQUIRED", "Sign in to continue"));
    }

    private boolean hasPhoto(UserAccount user) {
        return user.getPhoto() != null && user.getPhoto().length > 0;
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
        List<AccessRoleDefinition> defined = roleDefinitions.findByTenantIdOrderByLabelAsc(user.getTenantId());
        Map<String, AccessRoleDefinition> custom = defined.stream()
                .collect(Collectors.toMap(AccessRoleDefinition::getCode, Function.identity(), (left, right) -> left));
        List<UUID> roleIds = defined.stream().map(AccessRoleDefinition::getId).toList();
        Map<UUID, List<String>> storedPermissions = roleIds.isEmpty()
                ? Map.of()
                : rolePermissions.findByRoleIdIn(roleIds).stream()
                .collect(Collectors.groupingBy(AccessRolePermission::getRoleId,
                        Collectors.mapping(AccessRolePermission::getPermissionCode, Collectors.toList())));
        if (!ordered.isEmpty()) {
            AccessRoleDefinition customRole = custom.get(ordered.get(0));
            roleLabel = customRole == null ? RolePermissions.label(ordered.get(0)) : customRole.getLabel();
        }
        for (String role : ordered) {
            authorities.add("ROLE_" + role);
            if (RolePermissions.isSystem(role)) {
                authorities.addAll(RolePermissions.permissions(role));
            } else {
                AccessRoleDefinition customRole = custom.get(role);
                if (customRole != null) {
                    authorities.addAll(storedPermissions.getOrDefault(customRole.getId(), List.of()));
                }
            }
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
