package com.shambanexa.implementation;

import com.shambanexa.common.AccessScopes;
import com.shambanexa.common.ApiException;
import com.shambanexa.common.RolePermissions;
import com.shambanexa.dto.AccessAssignmentRequest;
import com.shambanexa.dto.UserAccessView;
import com.shambanexa.service.AuditTrail;
import com.shambanexa.dto.AccessCatalogResponse;
import com.shambanexa.dto.AccessCatalogResponse.AssignedUser;
import com.shambanexa.dto.AccessCatalogResponse.AuditRow;
import com.shambanexa.dto.AccessCatalogResponse.ModuleGrant;
import com.shambanexa.dto.AccessCatalogResponse.RoleCard;
import com.shambanexa.entity.Farm;
import com.shambanexa.entity.UserAccount;
import com.shambanexa.entity.UserRoleGrant;
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
    private final AuditEventRepository audits;
    private final AuditTrail audit;

    public AccessCatalogService(AccessGuard access, UserAccountRepository users, UserRoleGrantRepository grants,
                                FarmRepository farms, AuditEventRepository audits, AuditTrail audit) {
        this.access = access;
        this.users = users;
        this.grants = grants;
        this.farms = farms;
        this.audits = audits;
        this.audit = audit;
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
        List<RoleCard> roles = roleCodes(stored).stream()
                .map(code -> toRole(code, stored, people, farmNames))
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
        return new AccessCatalogResponse(roles, activity);
    }

    @Transactional(readOnly = true)
    public List<UserAccessView> users() {
        requireAdmin();
        UUID tenantId = access.tenantId();
        Map<UUID, String> farmNames = farms.findByTenantIdOrderByNameAsc(tenantId).stream()
                .collect(Collectors.toMap(Farm::getId, Farm::getName));
        List<UserRoleGrant> stored = grants.findByTenantId(tenantId);
        return users.findByTenantIdOrderByFullNameAsc(tenantId).stream()
                .map(user -> toUser(user, stored, farmNames))
                .toList();
    }

    @Transactional
    public UserAccessView assign(UUID userId, AccessAssignmentRequest request) {
        requireAdmin();
        if (!Set.of(RolePermissions.TENANT_ADMIN, RolePermissions.FARM_MANAGER, RolePermissions.FARM_EXPERT, RolePermissions.AUDITOR).contains(request.roleCode())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ROLE_UNKNOWN", "Choose Super Admin, Farm Manager, Farm Adviser, or Auditor");
        }
        if (!AccessScopes.known(request.accessScope())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SCOPE_UNKNOWN", "Choose Own Farm Only, Selected Farms, or All Farms");
        }
        UUID tenantId = access.tenantId();
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
                user.getFullName() + " · " + RolePermissions.label(request.roleCode()) + " · " + AccessScopes.label(scope) + " · " + farmsLabel);
        Map<UUID, String> names = farmMap.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().getName()));
        return toUser(user, grants.findByUserId(userId), names);
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

    private UserAccessView toUser(UserAccount user, List<UserRoleGrant> stored, Map<UUID, String> farmNames) {
        List<UserRoleGrant> mine = stored.stream().filter(grant -> user.getId().equals(grant.getUserId())).toList();
        String roleCode = mine.stream().map(UserRoleGrant::getRoleCode).findFirst().orElse(RolePermissions.FARM_MANAGER);
        List<UUID> farmIds = mine.stream().map(UserRoleGrant::getFarmId).filter(id -> id != null).distinct().toList();
        List<String> names = farmIds.stream().map(id -> farmNames.getOrDefault(id, "Farm")).sorted().toList();
        String scope = user.getAccessScope() == null ? AccessScopes.SELECTED_FARMS : user.getAccessScope();
        return new UserAccessView(user.getId(), user.getFullName(), user.getEmail(), roleCode,
                RolePermissions.label(roleCode), scope, farmIds, names);
    }

    private List<String> roleCodes(List<UserRoleGrant> stored) {
        LinkedHashSet<String> codes = new LinkedHashSet<>();
        codes.add(RolePermissions.TENANT_ADMIN);
        codes.add(RolePermissions.FARM_MANAGER);
        codes.add(RolePermissions.FARM_EXPERT);
        codes.add(RolePermissions.AUDITOR);
        stored.stream()
                .map(UserRoleGrant::getRoleCode)
                .sorted(Comparator.naturalOrder())
                .forEach(codes::add);
        return new ArrayList<>(codes);
    }

    private RoleCard toRole(String code, List<UserRoleGrant> stored, Map<UUID, UserAccount> people, Map<UUID, String> farmNames) {
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
        List<ModuleGrant> modules = RolePermissions.modules(code).stream()
                .map(module -> new ModuleGrant(
                        module.id(), module.label(), module.view(), module.create(), module.edit(),
                        module.delete(), module.approve(), module.export(), module.basis()
                ))
                .toList();
        return new RoleCard(
                code,
                RolePermissions.label(code),
                RolePermissions.description(code),
                RolePermissions.TENANT_ADMIN.equals(code) || RolePermissions.FARM_MANAGER.equals(code)
                        || RolePermissions.FARM_EXPERT.equals(code) || RolePermissions.AUDITOR.equals(code),
                scope,
                scopeLabel,
                assigned.size(),
                RolePermissions.canManageUsers(code),
                false,
                modules,
                assigned
        );
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
