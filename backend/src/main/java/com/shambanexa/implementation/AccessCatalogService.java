package com.shambanexa.implementation;

import com.shambanexa.common.AccessScopes;
import com.shambanexa.common.ApiException;
import com.shambanexa.common.RolePermissions;
import com.shambanexa.dto.AccessAssignmentRequest;
import com.shambanexa.dto.CreateRoleRequest;
import com.shambanexa.dto.RoleCreatedResponse;
import com.shambanexa.dto.RolePermissionRequest;
import com.shambanexa.dto.UserAccessView;
import com.shambanexa.service.AuditTrail;
import com.shambanexa.dto.AccessCatalogResponse;
import com.shambanexa.dto.AccessCatalogResponse.AssignedUser;
import com.shambanexa.dto.AccessCatalogResponse.AuditRow;
import com.shambanexa.dto.AccessCatalogResponse.ModuleGrant;
import com.shambanexa.dto.AccessCatalogResponse.PermissionOption;
import com.shambanexa.dto.AccessCatalogResponse.RoleCard;
import com.shambanexa.entity.AccessRoleDefinition;
import com.shambanexa.entity.AccessRolePermission;
import com.shambanexa.entity.Farm;
import com.shambanexa.entity.UserAccount;
import com.shambanexa.entity.UserRoleGrant;
import com.shambanexa.repository.AccessRoleDefinitionRepository;
import com.shambanexa.repository.AccessRolePermissionRepository;
import com.shambanexa.repository.AuditEventRepository;
import com.shambanexa.repository.FarmRepository;
import com.shambanexa.repository.UserAccountRepository;
import com.shambanexa.repository.UserRoleGrantRepository;
import com.shambanexa.security.AccessGuard;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AccessCatalogService {

    private final AccessGuard access;
    private final UserAccountRepository users;
    private final UserRoleGrantRepository grants;
    private final FarmRepository farms;
    private static final Set<String> RESERVED_LABELS = Set.of(
            "super admin", "farm manager", "farm adviser", "farm advisor", "farm expert", "auditor"
    );

    private final AuditEventRepository audits;
    private final AuditTrail audit;
    private final AccessRoleDefinitionRepository roleDefinitions;
    private final AccessRolePermissionRepository rolePermissions;

    public AccessCatalogService(AccessGuard access, UserAccountRepository users, UserRoleGrantRepository grants,
                                FarmRepository farms, AuditEventRepository audits, AuditTrail audit,
                                AccessRoleDefinitionRepository roleDefinitions, AccessRolePermissionRepository rolePermissions) {
        this.access = access;
        this.users = users;
        this.grants = grants;
        this.farms = farms;
        this.audits = audits;
        this.audit = audit;
        this.roleDefinitions = roleDefinitions;
        this.rolePermissions = rolePermissions;
    }

    @Transactional(readOnly = true)
    public AccessCatalogResponse current() {
        requireAdmin();
        UUID tenantId = access.tenantId();
        Map<UUID, UserAccount> people = users.findByTenantIdOrderByFullNameAsc(tenantId).stream()
                .collect(Collectors.toMap(UserAccount::getId, Function.identity()));
        Map<UUID, String> farmNames = farms.findByTenantIdOrderByNameAsc(tenantId).stream()
                .collect(Collectors.toMap(Farm::getId, Farm::getName));
        List<UserRoleGrant> stored = grants.findByTenantId(tenantId);
        List<AccessRoleDefinition> defined = roleDefinitions.findByTenantIdOrderByLabelAsc(tenantId);
        Map<String, List<String>> customPermissions = permissionsByCode(defined);
        List<RoleCard> roles = roleCodes(stored, defined).stream()
                .map(code -> toRole(code, stored, people, farmNames, definition(defined, code), customPermissions.getOrDefault(code, List.of())))
                .toList();
        List<AuditRow> activity = audits.findTop40ByTenantIdOrderByRecordedAtDesc(tenantId).stream()
                .map(event -> new AuditRow(
                        event.getId(),
                        event.getAction(),
                        event.getEntityType(),
                        event.getDetail() == null ? "" : event.getDetail(),
                        actorName(people, event.getActorId()),
                        event.getRecordedAt()
                ))
                .toList();
        List<PermissionOption> permissions = RolePermissions.CHOICES.stream()
                .map(choice -> new PermissionOption(choice.code(), choice.label(), choice.detail()))
                .toList();
        return new AccessCatalogResponse(roles, activity, permissions);
    }

    @Transactional
    public RoleCreatedResponse createRole(CreateRoleRequest request) {
        requireAdmin();
        String label = request.label() == null ? "" : request.label().trim().replaceAll("\\s+", " ");
        if (label.length() < 2 || label.length() > 80) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ROLE_NAME", "Enter a role name of 2 to 80 characters");
        }
        if (RESERVED_LABELS.contains(label.toLowerCase(Locale.ROOT))) {
            throw new ApiException(HttpStatus.CONFLICT, "ROLE_RESERVED", "Super Admin, Farm Manager, Farm Adviser, and Auditor keep their recorded names");
        }
        String description = request.description() == null ? "" : request.description().trim();
        if (description.length() > 400) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ROLE_DESCRIPTION", "Keep the description to 400 characters");
        }
        if (description.isEmpty()) {
            description = "Custom role for this workspace.";
        }
        List<String> permissions = requirePermissions(request.permissions());
        String code = codeFor(label);
        if (RolePermissions.isSystem(code)) {
            throw new ApiException(HttpStatus.CONFLICT, "ROLE_RESERVED", "Super Admin, Farm Manager, Farm Adviser, and Auditor keep their recorded names");
        }
        UUID tenantId = access.tenantId();
        if (roleDefinitions.existsByTenantIdAndCode(tenantId, code)) {
            throw new ApiException(HttpStatus.CONFLICT, "ROLE_EXISTS", "A role with this name is already stored");
        }
        AccessRoleDefinition role = new AccessRoleDefinition();
        role.setId(UUID.randomUUID());
        role.setTenantId(tenantId);
        role.setCode(code);
        role.setLabel(label);
        role.setDescription(description);
        roleDefinitions.save(role);
        savePermissions(role.getId(), permissions);
        audit.record("ROLE_CREATED", "ROLE", role.getId(), label + " · " + String.join(", ", permissions));
        audit.activity("ACCESS", "Role created", label, null);
        return new RoleCreatedResponse(code, label);
    }

    @Transactional
    public void updatePermissions(String code, RolePermissionRequest request) {
        requireAdmin();
        if (RolePermissions.isSystem(code)) {
            throw new ApiException(HttpStatus.CONFLICT, "ROLE_LOCKED", "System roles keep their recorded permissions");
        }
        UUID tenantId = access.tenantId();
        AccessRoleDefinition role = roleDefinitions.findByTenantIdAndCode(tenantId, code)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ROLE_NOT_FOUND", "Role not found"));
        List<String> permissions = requirePermissions(request.permissions());
        rolePermissions.deleteByRoleId(role.getId());
        rolePermissions.flush();
        savePermissions(role.getId(), permissions);
        audit.record("ROLE_PERMISSIONS_CHANGED", "ROLE", role.getId(), role.getLabel() + " · " + String.join(", ", permissions));
        audit.activity("ACCESS", "Role permissions changed", role.getLabel(), null);
    }

    @Transactional(readOnly = true)
    public List<UserAccessView> users() {
        requireAdmin();
        UUID tenantId = access.tenantId();
        Map<UUID, String> farmNames = farms.findByTenantIdOrderByNameAsc(tenantId).stream()
                .collect(Collectors.toMap(Farm::getId, Farm::getName));
        List<UserRoleGrant> stored = grants.findByTenantId(tenantId);
        Map<String, String> labels = roleLabels(tenantId);
        return users.findByTenantIdOrderByFullNameAsc(tenantId).stream()
                .filter(user -> stored.stream().anyMatch(grant -> user.getId().equals(grant.getUserId())))
                .map(user -> toUser(user, stored, farmNames, labels))
                .toList();
    }

    @Transactional
    public UserAccessView assign(UUID userId, AccessAssignmentRequest request) {
        requireAdmin();
        UUID tenantId = access.tenantId();
        if (!knownRole(tenantId, request.roleCode())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ROLE_UNKNOWN", "Choose a role stored for this workspace");
        }
        if (!AccessScopes.known(request.accessScope())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SCOPE_UNKNOWN", "Choose Own Farm Only, Selected Farms, or All Farms");
        }
        UserAccount user = users.findById(userId)
                .filter(candidate -> tenantId.equals(candidate.getTenantId()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found"));
        String scope = RolePermissions.TENANT_ADMIN.equals(request.roleCode())
                ? AccessScopes.ALL_FARMS
                : request.accessScope();
        List<UUID> farmIds = request.farmIds() == null ? List.of() : request.farmIds().stream().distinct().toList();
        Map<UUID, Farm> farmMap = farms.findByTenantIdOrderByNameAsc(tenantId).stream()
                .collect(Collectors.toMap(Farm::getId, Function.identity()));
        if (!AccessScopes.ALL_FARMS.equals(scope)) {
            if (farmIds.isEmpty() || farmIds.stream().anyMatch(id -> !farmMap.containsKey(id))) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "FARM_SCOPE_INVALID", "Choose farms that belong to this workspace");
            }
            if (AccessScopes.OWN_FARM.equals(scope) && farmIds.size() != 1) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "FARM_SCOPE_INVALID", "Own Farm Only uses one farm");
            }
        }
        List<UserRoleGrant> existing = grants.findByUserId(userId);
        boolean wasAdmin = existing.stream().anyMatch(grant -> RolePermissions.TENANT_ADMIN.equals(grant.getRoleCode()));
        if (wasAdmin && !RolePermissions.TENANT_ADMIN.equals(request.roleCode()) && adminCount(tenantId) <= 1) {
            throw new ApiException(HttpStatus.CONFLICT, "LAST_ADMIN", "The last administrator keeps All Farms access");
        }
        grants.deleteAll(existing);
        grants.flush();
        if (AccessScopes.ALL_FARMS.equals(scope)) {
            saveGrant(tenantId, userId, request.roleCode(), null);
        } else {
            for (UUID farmId : farmIds) {
                saveGrant(tenantId, userId, request.roleCode(), farmId);
            }
        }
        user.setAccessScope(scope);
        users.save(user);
        String farmsLabel = AccessScopes.ALL_FARMS.equals(scope)
                ? "every farm"
                : farmIds.stream().map(id -> farmMap.get(id).getName()).sorted().collect(Collectors.joining(", "));
        audit.record("ACCESS_SCOPE_CHANGED", "USER", userId,
                user.getFullName() + " · " + roleLabels(tenantId).getOrDefault(request.roleCode(), RolePermissions.label(request.roleCode()))
                        + " · " + AccessScopes.label(scope) + " · " + farmsLabel);
        audit.activity("ACCESS", "Access changed", user.getFullName() + " · " + AccessScopes.label(scope), null);
        Map<UUID, String> names = farmMap.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().getName()));
        return toUser(user, grants.findByUserId(userId), names, roleLabels(tenantId));
    }

    private void requireAdmin() {
        if (!access.canManageAccess()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "You do not have permission to do that");
        }
    }

    private long adminCount(UUID tenantId) {
        return grants.findByTenantId(tenantId).stream()
                .filter(grant -> RolePermissions.TENANT_ADMIN.equals(grant.getRoleCode()))
                .map(UserRoleGrant::getUserId)
                .distinct()
                .count();
    }

    private void saveGrant(UUID tenantId, UUID userId, String roleCode, UUID farmId) {
        UserRoleGrant grant = new UserRoleGrant();
        grant.setTenantId(tenantId);
        grant.setUserId(userId);
        grant.setRoleCode(roleCode);
        grant.setFarmId(farmId);
        grants.save(grant);
    }

    private UserAccessView toUser(UserAccount user, List<UserRoleGrant> stored, Map<UUID, String> farmNames, Map<String, String> labels) {
        List<UserRoleGrant> mine = stored.stream().filter(grant -> user.getId().equals(grant.getUserId())).toList();
        String roleCode = mine.stream().map(UserRoleGrant::getRoleCode).findFirst().orElse(RolePermissions.FARM_MANAGER);
        List<UUID> farmIds = mine.stream().map(UserRoleGrant::getFarmId).filter(id -> id != null).distinct().toList();
        List<String> names = farmIds.stream().map(id -> farmNames.getOrDefault(id, "Farm")).sorted().toList();
        String scope = user.getAccessScope() == null ? AccessScopes.SELECTED_FARMS : user.getAccessScope();
        return new UserAccessView(user.getId(), user.getFullName(), user.getEmail(), roleCode,
                labels.getOrDefault(roleCode, RolePermissions.label(roleCode)), scope, farmIds, names);
    }

    private List<String> roleCodes(List<UserRoleGrant> stored, List<AccessRoleDefinition> defined) {
        LinkedHashSet<String> codes = new LinkedHashSet<>();
        codes.add(RolePermissions.TENANT_ADMIN);
        codes.add(RolePermissions.FARM_MANAGER);
        codes.add(RolePermissions.FARM_EXPERT);
        codes.add(RolePermissions.AUDITOR);
        defined.stream()
                .map(AccessRoleDefinition::getCode)
                .sorted(Comparator.naturalOrder())
                .forEach(codes::add);
        stored.stream()
                .map(UserRoleGrant::getRoleCode)
                .sorted(Comparator.naturalOrder())
                .forEach(codes::add);
        return new ArrayList<>(codes);
    }

    private RoleCard toRole(String code, List<UserRoleGrant> stored, Map<UUID, UserAccount> people, Map<UUID, String> farmNames,
                            AccessRoleDefinition defined, List<String> customPermissions) {
        List<UserRoleGrant> mine = stored.stream().filter(grant -> code.equals(grant.getRoleCode())).toList();
        boolean allFarms = mine.stream().anyMatch(grant -> grant.getFarmId() == null);
        List<AssignedUser> assigned = mine.stream()
                .map(UserRoleGrant::getUserId)
                .distinct()
                .map(people::get)
                .filter(user -> user != null)
                .sorted(Comparator.comparing(UserAccount::getFullName))
                .map(user -> new AssignedUser(
                        user.getId(),
                        user.getFullName(),
                        user.getEmail(),
                        user.getJobTitle(),
                        user.getStatus(),
                        farmNamesFor(user.getId(), mine, farmNames)
                ))
                .toList();
        List<String> names = mine.stream()
                .map(UserRoleGrant::getFarmId)
                .filter(id -> id != null)
                .distinct()
                .map(id -> farmNames.getOrDefault(id, "Farm"))
                .sorted()
                .toList();
        String scope = allFarms ? AccessScopes.ALL_FARMS : AccessScopes.SELECTED_FARMS;
        String scopeLabel = allFarms ? "All Farms" : (names.isEmpty() ? "Assigned farms" : String.join(", ", names));
        boolean system = RolePermissions.isSystem(code);
        List<ModuleGrant> modules = (defined == null ? RolePermissions.modules(code) : RolePermissions.modules(customPermissions, false)).stream()
                .map(module -> new ModuleGrant(
                        module.id(), module.label(), module.view(), module.create(), module.edit(),
                        module.delete(), module.approve(), module.export(), module.basis()
                ))
                .toList();
        return new RoleCard(
                code,
                defined == null ? RolePermissions.label(code) : defined.getLabel(),
                defined == null ? RolePermissions.description(code) : defined.getDescription(),
                system,
                scope,
                scopeLabel,
                assigned.size(),
                system && RolePermissions.canManageUsers(code),
                false,
                modules,
                assigned
        );
    }

    private boolean knownRole(UUID tenantId, String code) {
        return RolePermissions.isSystem(code) || roleDefinitions.existsByTenantIdAndCode(tenantId, code);
    }

    private Map<String, String> roleLabels(UUID tenantId) {
        return roleDefinitions.findByTenantIdOrderByLabelAsc(tenantId).stream()
                .collect(Collectors.toMap(AccessRoleDefinition::getCode, AccessRoleDefinition::getLabel, (left, right) -> left));
    }

    private AccessRoleDefinition definition(List<AccessRoleDefinition> defined, String code) {
        return defined.stream().filter(role -> code.equals(role.getCode())).findFirst().orElse(null);
    }

    private Map<String, List<String>> permissionsByCode(List<AccessRoleDefinition> defined) {
        if (defined.isEmpty()) {
            return Map.of();
        }
        Map<UUID, String> codes = defined.stream()
                .collect(Collectors.toMap(AccessRoleDefinition::getId, AccessRoleDefinition::getCode));
        List<UUID> ids = defined.stream().map(AccessRoleDefinition::getId).toList();
        Map<String, List<String>> grouped = new java.util.HashMap<>();
        for (AccessRolePermission permission : rolePermissions.findByRoleIdIn(ids)) {
            String code = codes.get(permission.getRoleId());
            if (code != null) {
                grouped.computeIfAbsent(code, key -> new ArrayList<>()).add(permission.getPermissionCode());
            }
        }
        return grouped;
    }

    private List<String> requirePermissions(List<String> selected) {
        List<String> permissions = RolePermissions.normalize(selected);
        if (permissions == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PERMISSION_UNKNOWN", "Choose a permission this workspace checks");
        }
        if (permissions.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PERMISSION_REQUIRED", "Choose at least one permission");
        }
        return permissions;
    }

    private void savePermissions(UUID roleId, List<String> permissions) {
        for (String permission : permissions) {
            AccessRolePermission row = new AccessRolePermission();
            row.setRoleId(roleId);
            row.setPermissionCode(permission);
            rolePermissions.save(row);
        }
    }

    private String codeFor(String label) {
        String code = label.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_").replaceAll("^_+|_+$", "");
        if (code.length() > 40) {
            code = code.substring(0, 40).replaceAll("_+$", "");
        }
        if (code.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ROLE_NAME", "Enter a role name of 2 to 80 characters");
        }
        return code;
    }

    private List<String> farmNamesFor(UUID userId, List<UserRoleGrant> grants, Map<UUID, String> farmNames) {
        return grants.stream()
                .filter(grant -> userId.equals(grant.getUserId()) && grant.getFarmId() != null)
                .map(grant -> farmNames.getOrDefault(grant.getFarmId(), "Farm"))
                .distinct()
                .sorted()
                .toList();
    }

    private String actorName(Map<UUID, UserAccount> people, UUID actorId) {
        if (actorId == null) {
            return "System";
        }
        UserAccount actor = people.get(actorId);
        return actor == null ? "Unknown user" : actor.getFullName();
    }
}
