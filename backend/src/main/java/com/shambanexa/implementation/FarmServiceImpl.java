package com.shambanexa.implementation;

import com.shambanexa.service.FarmService;
import com.shambanexa.service.AuditTrail;
import com.shambanexa.service.PopulationService;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shambanexa.common.BoundaryGeometry;
import com.shambanexa.common.RolePermissions;
import com.shambanexa.dto.FacilityResponse;
import com.shambanexa.dto.FarmDetail;
import com.shambanexa.dto.FarmRequest;
import com.shambanexa.dto.FarmResponse;
import com.shambanexa.dto.GeoPoint;
import com.shambanexa.common.ApiException;
import com.shambanexa.entity.Farm;
import com.shambanexa.entity.Facility;
import com.shambanexa.entity.UserRoleGrant;
import com.shambanexa.repository.FacilityRepository;
import com.shambanexa.repository.FarmRepository;
import com.shambanexa.repository.UserRoleGrantRepository;
import com.shambanexa.security.AccessGuard;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class FarmServiceImpl implements FarmService {

    private static final TypeReference<List<GeoPoint>> BOUNDARY = new TypeReference<>() {
    };

    private final FarmRepository farms;
    private final FacilityRepository facilities;
    private final UserRoleGrantRepository grants;
    private final PopulationService population;
    private final AccessGuard access;
    private final AuditTrail audit;
    private final ObjectMapper json;

    public FarmServiceImpl(FarmRepository farms, FacilityRepository facilities, UserRoleGrantRepository grants,
                       PopulationService population, AccessGuard access, AuditTrail audit, ObjectMapper json) {
        this.farms = farms;
        this.facilities = facilities;
        this.grants = grants;
        this.population = population;
        this.access = access;
        this.audit = audit;
        this.json = json;
    }

    @Transactional(readOnly = true)
    public List<FarmResponse> list() {
        Map<UUID, Long> counts = population.byFarm();
        return visibleFarms().stream().map(farm -> toResponse(farm, counts)).toList();
    }

    @Transactional(readOnly = true)
    public FarmDetail detail(UUID id) {
        Farm farm = load(id);
        List<FacilityResponse> facilityResponses = facilities
                .findByTenantIdAndFarmIdOrderByNameAsc(access.tenantId(), id)
                .stream()
                .map(this::toFacility)
                .toList();
        return new FarmDetail(toResponse(farm, population.byFarm()), facilityResponses);
    }

    @Transactional
    public FarmResponse create(FarmRequest request) {
        access.requireAuthority("FARM_WRITE");
        boolean onboarding = !access.allFarms() && access.current().farmIds().isEmpty();
        if (!access.canManageAccess() && !onboarding) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FARM_FORBIDDEN", "Only an administrator can register another farm");
        }
        String code = request.code().trim().toUpperCase();
        if (farms.existsByTenantIdAndCodeIgnoreCase(access.tenantId(), code)) {
            throw new ApiException(HttpStatus.CONFLICT, "DUPLICATE_FARM_CODE", "That farm code is already used");
        }
        Farm farm = new Farm();
        farm.setId(UUID.randomUUID());
        farm.setVersion(null);
        farm.setTenantId(access.tenantId());
        farm.setCode(code);
        apply(farm, request);
        farm = farms.saveAndFlush(farm);

        Facility grazing = new Facility();
        grazing.setTenantId(access.tenantId());
        grazing.setFarmId(farm.getId());
        grazing.setCode(code + "-GRZ");
        grazing.setName(farm.getName() + " Grazing");
        grazing.setFacilityType("GRAZING");
        grazing.setStatus("ACTIVE");
        grazing.setSupervisorName(farm.getManagerName());
        facilities.save(grazing);

        if (onboarding) {
            UserRoleGrant grant = new UserRoleGrant();
            grant.setTenantId(access.tenantId());
            grant.setUserId(access.current().id());
            grant.setRoleCode(RolePermissions.FARM_MANAGER);
            grant.setFarmId(farm.getId());
            grants.save(grant);
            audit.record("ACCESS_GRANTED", "USER", access.current().id(),
                    "Own Farm Only · " + farm.getName());
        }
        audit.record("FARM_REGISTERED", "FARM", farm.getId(), farm.getName());
        audit.activity("FARM", "New farm registered", farm.getName(), farm.getId());
        return toResponse(farm, Map.of());
    }

    @Transactional
    public FarmResponse update(UUID id, FarmRequest request) {
        access.requireAuthority("FARM_WRITE");
        Farm farm = load(id);
        if (request.version() == null || !request.version().equals(farm.getVersion())) {
            throw new ApiException(HttpStatus.CONFLICT, "VERSION_CONFLICT",
                    "This farm was updated by someone else. Refresh and try again.");
        }
        apply(farm, request);
        farms.save(farm);
        audit.record("FARM_UPDATED", "FARM", farm.getId(), farm.getName());
        audit.activity("FARM", "Farm updated", farm.getName(), farm.getId());
        return toResponse(farm, population.byFarm());
    }

    public List<Farm> visibleFarms() {
        return farms.findByTenantIdOrderByNameAsc(access.tenantId()).stream()
                .filter(farm -> access.canReadFarm(farm.getId()))
                .toList();
    }

    public Farm load(UUID id) {
        Farm farm = farms.findByIdAndTenantId(id, access.tenantId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "FARM_NOT_FOUND", "Farm not found"));
        access.checkFarm(farm.getId());
        return farm;
    }

    private void apply(Farm farm, FarmRequest request) {
        farm.setName(request.name().trim());
        farm.setRegion(request.region().trim());
        farm.setLatitude(request.latitude());
        farm.setLongitude(request.longitude());
        farm.setAddress(request.address().trim());
        farm.setManagerName(blankToNull(request.managerName()));
        farm.setContactPhone(blankToNull(request.contactPhone()));
        if (farm.getTimezone() == null) {
            farm.setTimezone("Africa/Dar_es_Salaam");
        }
        if (request.boundary() != null) {
            String issue = BoundaryGeometry.problem(request.boundary());
            if (issue != null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "BOUNDARY_INVALID", issue);
            }
            farm.setBoundary(request.boundary().isEmpty() ? null : writeBoundary(request.boundary()));
        }
    }

    private FarmResponse toResponse(Farm farm, Map<UUID, Long> counts) {
        int facilityCount = facilities.findByTenantIdAndFarmIdOrderByNameAsc(farm.getTenantId(), farm.getId()).size();
        return new FarmResponse(
                farm.getId(),
                farm.getCode(),
                farm.getName(),
                farm.getRegion(),
                farm.getStatus(),
                farm.getLatitude(),
                farm.getLongitude(),
                farm.getTimezone(),
                farm.getAddress(),
                farm.getManagerName(),
                farm.getContactPhone(),
                farm.getEstablishedOn(),
                counts.getOrDefault(farm.getId(), 0L),
                facilityCount,
                farm.getVersion(),
                farm.getUpdatedAt(),
                readBoundary(farm.getBoundary())
        );
    }

    private FacilityResponse toFacility(Facility facility) {
        return new FacilityResponse(
                facility.getId(),
                facility.getCode(),
                facility.getName(),
                facility.getFacilityType(),
                facility.getStatus(),
                facility.getCapacity(),
                facility.getCapacityUnit(),
                facility.getSupervisorName(),
                facility.getCreatedAt()
        );
    }

    private String writeBoundary(List<GeoPoint> boundary) {
        try {
            return json.writeValueAsString(boundary);
        } catch (Exception ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "BOUNDARY_INVALID", "The farm boundary could not be stored");
        }
    }

    private List<GeoPoint> readBoundary(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        try {
            return json.readValue(raw, BOUNDARY);
        } catch (Exception ex) {
            return List.of();
        }
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
