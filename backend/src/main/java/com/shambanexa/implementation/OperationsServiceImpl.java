package com.shambanexa.implementation;

import com.shambanexa.service.OperationsService;
import com.shambanexa.service.AuditTrail;
import com.shambanexa.service.FarmService;

import com.shambanexa.dto.AnimalHealthRequest;
import com.shambanexa.dto.AnimalHealthResponse;
import com.shambanexa.dto.BreedResponse;
import com.shambanexa.dto.DiseaseReportRequest;
import com.shambanexa.dto.DiseaseReportResponse;
import com.shambanexa.dto.FishCycleResponse;
import com.shambanexa.dto.HealthCaseResponse;
import com.shambanexa.dto.ProductionRequest;
import com.shambanexa.dto.ProductionResponse;
import com.shambanexa.dto.SearchHit;
import com.shambanexa.dto.SettingsResponse;
import com.shambanexa.dto.SettingsUpdateRequest;
import com.shambanexa.dto.SpeciesResponse;
import com.shambanexa.dto.StaffRequest;
import com.shambanexa.dto.StaffResponse;
import com.shambanexa.dto.TaskResponse;
import com.shambanexa.common.ApiException;
import com.shambanexa.entity.Alert;
import com.shambanexa.entity.Animal;
import com.shambanexa.entity.HerdBatch;
import com.shambanexa.entity.Species;
import com.shambanexa.entity.Farm;
import com.shambanexa.entity.FishCycle;
import com.shambanexa.entity.HealthCase;
import com.shambanexa.entity.InventoryItem;
import com.shambanexa.entity.ProductionRecord;
import com.shambanexa.entity.StaffMember;
import com.shambanexa.entity.Tenant;
import com.shambanexa.repository.AlertRepository;
import com.shambanexa.repository.AnimalRepository;
import com.shambanexa.repository.BreedRepository;
import com.shambanexa.repository.FishCycleRepository;
import com.shambanexa.repository.HerdBatchRepository;
import com.shambanexa.repository.HealthCaseRepository;
import com.shambanexa.repository.InventoryItemRepository;
import com.shambanexa.repository.ProductionRecordRepository;
import com.shambanexa.repository.SpeciesRepository;
import com.shambanexa.repository.StaffMemberRepository;
import com.shambanexa.repository.TenantRepository;
import com.shambanexa.repository.WorkTaskRepository;
import com.shambanexa.security.AccessGuard;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Locale;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class OperationsServiceImpl implements OperationsService {

    private final ProductionRecordRepository production;
    private final InventoryItemRepository inventory;
    private final StaffMemberRepository staff;
    private final WorkTaskRepository tasks;
    private final FishCycleRepository fishCycles;
    private final HealthCaseRepository healthCases;
    private final AlertRepository alerts;
    private final SpeciesRepository species;
    private final BreedRepository breeds;
    private final AnimalRepository animals;
    private final HerdBatchRepository batches;
    private final TenantRepository tenants;
    private final FarmService farms;
    private final AccessGuard access;
    private final AuditTrail audit;

    public OperationsServiceImpl(ProductionRecordRepository production, InventoryItemRepository inventory,
                             StaffMemberRepository staff, WorkTaskRepository tasks, FishCycleRepository fishCycles,
                             HealthCaseRepository healthCases, AlertRepository alerts, SpeciesRepository species,
                             BreedRepository breeds, AnimalRepository animals, HerdBatchRepository batches,
                             TenantRepository tenants, FarmService farms, AccessGuard access, AuditTrail audit) {
        this.production = production;
        this.inventory = inventory;
        this.staff = staff;
        this.tasks = tasks;
        this.fishCycles = fishCycles;
        this.healthCases = healthCases;
        this.alerts = alerts;
        this.species = species;
        this.breeds = breeds;
        this.animals = animals;
        this.batches = batches;
        this.tenants = tenants;
        this.farms = farms;
        this.access = access;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<ProductionResponse> production() {
        Map<UUID, Farm> farmMap = farmMap();
        return production.findByTenantIdOrderByRecordedOnDesc(access.tenantId()).stream()
                .filter(record -> access.canReadFarm(record.getFarmId()))
                .map(record -> new ProductionResponse(
                        record.getId(),
                        record.getFarmId(),
                        farmName(farmMap, record.getFarmId()),
                        record.getProductType(),
                        record.getQuantity(),
                        record.getUnit(),
                        record.getRecordedOn(),
                        record.getNotes()
                ))
                .toList();
    }

    @Transactional
    public ProductionResponse recordProduction(ProductionRequest request) {
        access.requireAuthority("ANIMAL_WRITE");
        access.checkFarm(request.farmId());
        ProductionRecord record = new ProductionRecord();
        record.setTenantId(access.tenantId());
        record.setFarmId(request.farmId());
        record.setProductType(request.productType().trim().toUpperCase());
        record.setQuantity(request.quantity());
        record.setUnit(request.unit().trim());
        record.setRecordedOn(request.recordedOn() == null
                ? LocalDate.now(ZoneId.of("Africa/Dar_es_Salaam"))
                : request.recordedOn());
        record.setNotes(blank(request.notes()));
        production.save(record);
        audit.record("PRODUCTION_RECORDED", "PRODUCTION", record.getId(), record.getProductType());
        audit.activity("PRODUCTION", "Production recorded",
                record.getQuantity().stripTrailingZeros().toPlainString() + " " + record.getUnit() + " " + record.getProductType(),
                record.getFarmId());
        return new ProductionResponse(record.getId(), record.getFarmId(), farms.load(record.getFarmId()).getName(),
                record.getProductType(), record.getQuantity(), record.getUnit(), record.getRecordedOn(), record.getNotes());
    }

    @Transactional(readOnly = true)
    public List<StaffResponse> staff() {
        Map<UUID, Farm> farmMap = farmMap();
        return staff.findByTenantIdOrderByFullNameAsc(access.tenantId()).stream()
                .filter(member -> access.canReadFarm(member.getFarmId()))
                .map(member -> new StaffResponse(
                        member.getId(),
                        member.getFullName(),
                        member.getRoleTitle(),
                        member.getFarmId(),
                        farmName(farmMap, member.getFarmId()),
                        member.getPhone(),
                        member.getStatus(),
                        member.getHiredOn()
                ))
                .toList();
    }

    @Transactional
    public StaffResponse createStaff(StaffRequest request) {
        access.requireAuthority("STAFF_WRITE");
        access.checkFarm(request.farmId());
        StaffMember member = new StaffMember();
        member.setTenantId(access.tenantId());
        member.setFarmId(request.farmId());
        member.setFullName(request.fullName().trim());
        member.setRoleTitle(request.roleTitle().trim());
        member.setPhone(blank(request.phone()));
        member.setHiredOn(request.hiredOn());
        member.setStatus("ACTIVE");
        staff.save(member);
        audit.record("STAFF_CREATED", "STAFF", member.getId(), member.getFullName());
        return new StaffResponse(member.getId(), member.getFullName(), member.getRoleTitle(), member.getFarmId(),
                farms.load(member.getFarmId()).getName(), member.getPhone(), member.getStatus(), member.getHiredOn());
    }

    @Transactional(readOnly = true)
    public List<SpeciesResponse> species() {
        return species.findByTenantIdOrderBySortOrderAsc(access.tenantId()).stream()
                .map(item -> new SpeciesResponse(item.getId(), item.getCode(), item.getName(), item.getCategory(), item.getChartColor()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<BreedResponse> breeds(UUID speciesId) {
        return breeds.findByTenantIdAndSpeciesIdOrderByNameAsc(access.tenantId(), speciesId).stream()
                .map(breed -> new BreedResponse(breed.getId(), breed.getName(), breed.getSpeciesId()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TaskResponse> tasks() {
        Map<UUID, Farm> farmMap = farmMap();
        return tasks.findByTenantIdOrderByDueOnAsc(access.tenantId()).stream()
                .filter(task -> access.canReadFarm(task.getFarmId()))
                .map(task -> new TaskResponse(
                        task.getId(),
                        task.getTitle(),
                        task.getTaskType(),
                        task.getStatus(),
                        task.getDueOn(),
                        task.getAssigneeName(),
                        task.getNotes(),
                        task.getFarmId(),
                        farmName(farmMap, task.getFarmId())
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<FishCycleResponse> fishCycles() {
        Map<UUID, Farm> farmMap = farmMap();
        return fishCycles.findByTenantIdOrderByStartedOnDesc(access.tenantId()).stream()
                .filter(cycle -> access.canReadFarm(cycle.getFarmId()))
                .map(cycle -> new FishCycleResponse(
                        cycle.getId(),
                        cycle.getCode(),
                        cycle.getStatus(),
                        cycle.getStockedQuantity(),
                        cycle.getMortality(),
                        cycle.getHarvested(),
                        estimated(cycle),
                        cycle.getStartedOn(),
                        cycle.getFarmId(),
                        farmName(farmMap, cycle.getFarmId())
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<HealthCaseResponse> healthCases() {
        Map<UUID, Farm> farmMap = farmMap();
        return healthCases.findByTenantIdOrderByOpenedOnDesc(access.tenantId()).stream()
                .filter(healthCase -> access.canReadFarm(healthCase.getFarmId()))
                .map(healthCase -> new HealthCaseResponse(
                        healthCase.getId(),
                        healthCase.getTitle(),
                        healthCase.getCaseStatus(),
                        healthCase.getSeverity(),
                        healthCase.getOpenedOn(),
                        healthCase.getNotes(),
                        healthCase.getFarmId(),
                        farmName(farmMap, healthCase.getFarmId())
                ))
                .toList();
    }

    @Transactional
    public DiseaseReportResponse reportDisease(DiseaseReportRequest request) {
        access.requireAuthority("ANIMAL_WRITE");
        access.checkFarm(request.farmId());
        String level = request.status().trim().toUpperCase();
        if (!level.equals("SUSPECTED") && !level.equals("OUTBREAK")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Choose suspected or outbreak");
        }
        ZoneId zone = ZoneId.of("Africa/Dar_es_Salaam");
        LocalDate today = LocalDate.now(zone);
        LocalDate noticed = request.noticedOn() == null ? today : request.noticedOn();
        if (noticed.isAfter(today)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "The date noticed cannot be in the future");
        }
        String disease = request.diseaseName().trim();
        String speciesName = blank(request.species());
        String signs = request.signs().trim();
        String severity = level.equals("OUTBREAK") ? "CRITICAL" : "WARNING";
        String notes = "Animals affected: " + request.animalsAffected()
                + (speciesName == null ? "" : ". Species: " + speciesName)
                + ". Signs: " + signs;

        HealthCase healthCase = new HealthCase();
        healthCase.setTenantId(access.tenantId());
        healthCase.setFarmId(request.farmId());
        healthCase.setTitle(disease);
        healthCase.setCaseStatus("OPEN");
        healthCase.setSeverity(severity);
        healthCase.setOpenedOn(noticed);
        healthCase.setNotes(clip(notes, 400));
        healthCases.save(healthCase);

        Farm farm = farms.load(request.farmId());
        String body = farm.getName() + " · " + request.animalsAffected() + " "
                + (speciesName == null ? "animals" : speciesName) + " · " + signs;
        Alert alert = new Alert();
        alert.setTenantId(access.tenantId());
        alert.setFarmId(request.farmId());
        alert.setSeverity(severity);
        alert.setTitle(clip("Disease outbreak: " + disease, 160));
        alert.setBody(clip(body, 400));
        alert.setStatus("OPEN");
        alert.setSourceType("DISEASE");
        alert.setSourceId(healthCase.getId());
        alert.setOccurredAt(noticed.atStartOfDay(zone).toInstant());
        alerts.save(alert);

        audit.record("DISEASE_REPORTED", "HEALTH_CASE", healthCase.getId(), disease);
        audit.activity("LIVESTOCK", "Disease reported",
                disease + " · " + request.animalsAffected() + " animals", request.farmId());
        return new DiseaseReportResponse(
                healthCase.getId(),
                alert.getId(),
                farm.getId(),
                farm.getName(),
                disease,
                request.animalsAffected(),
                severity,
                noticed,
                signs
        );
    }

    @Transactional
    public AnimalHealthResponse reportAnimalHealth(AnimalHealthRequest request) {
        access.requireAuthority("ANIMAL_WRITE");
        access.checkFarm(request.farmId());
        String outcome = request.outcome().trim().toUpperCase();
        if (!outcome.equals("SICK") && !outcome.equals("DEATH") && !outcome.equals("CURED")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Choose sick, death, or cured");
        }
        if (request.batchId() != null && request.animalId() != null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Choose a herd or one tagged animal");
        }
        ZoneId zone = ZoneId.of("Africa/Dar_es_Salaam");
        LocalDate today = LocalDate.now(zone);
        LocalDate occurred = request.occurredOn() == null ? today : request.occurredOn();
        if (occurred.isAfter(today)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "The date cannot be in the future");
        }

        Animal tagged = null;
        if (request.animalId() != null) {
            tagged = animals.findByIdAndTenantId(request.animalId(), access.tenantId())
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ANIMAL_NOT_FOUND", "Animal not found"));
            if (!tagged.getFarmId().equals(request.farmId())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "That animal is not on the selected farm");
            }
            if (request.animals() != 1) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "A tagged animal report counts as one animal");
            }
            if ("DEAD".equals(tagged.getStatus()) && !outcome.equals("DEATH")) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "That animal is already recorded as dead");
            }
            if ("DEAD".equals(tagged.getStatus())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "That animal is already recorded as dead");
            }
        }

        HerdBatch batch = null;
        Integer herdQuantity = null;
        if (request.batchId() != null) {
            batch = batches.findByIdAndTenantId(request.batchId(), access.tenantId())
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "BATCH_NOT_FOUND", "Herd not found"));
            if (!batch.getFarmId().equals(request.farmId())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "That herd is not on the selected farm");
            }
            if (outcome.equals("DEATH")) {
                int next = batch.getQuantity() - request.animals();
                if (next < 0) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Deaths cannot exceed the herd headcount");
                }
                batch.setQuantity(next);
                batches.save(batch);
                herdQuantity = next;
            }
        }

        if (tagged != null && outcome.equals("DEATH")) {
            tagged.setStatus("DEAD");
            animals.save(tagged);
        } else if (tagged != null && outcome.equals("SICK")) {
            tagged.setHealthStatus("SICK");
            animals.save(tagged);
        } else if (tagged != null) {
            tagged.setHealthStatus("HEALTHY");
            animals.save(tagged);
        }

        String speciesName = blank(request.species());
        if (speciesName == null && batch != null) {
            speciesName = species.findById(batch.getSpeciesId()).map(Species::getName).orElse(null);
        }
        String condition = blank(request.condition());
        String subject = speciesName == null ? "animals" : speciesName;
        String label = condition == null ? request.animals() + " " + subject : condition;
        String severity = outcome.equals("DEATH") ? "CRITICAL" : outcome.equals("SICK") ? "WARNING" : "INFO";
        String caseStatus = outcome.equals("SICK") ? "OPEN" : "CLOSED";
        String notes = "Animals: " + request.animals()
                + (speciesName == null ? "" : ". Species: " + speciesName)
                + ". Notes: " + request.notes().trim();

        HealthCase healthCase = new HealthCase();
        healthCase.setTenantId(access.tenantId());
        healthCase.setFarmId(request.farmId());
        healthCase.setAnimalId(tagged == null ? null : tagged.getId());
        healthCase.setTitle(clip(label, 160));
        healthCase.setCaseStatus(caseStatus);
        healthCase.setSeverity(severity);
        healthCase.setOpenedOn(occurred);
        healthCase.setNotes(clip(notes, 400));
        healthCases.save(healthCase);

        if (outcome.equals("CURED") && tagged == null) {
            alerts.findByTenantIdAndStatusOrderByOccurredAtDesc(access.tenantId(), "OPEN").stream()
                    .filter(alert -> request.farmId().equals(alert.getFarmId()))
                    .filter(alert -> "DISEASE".equals(alert.getSourceType()) || "SICK".equals(alert.getSourceType()))
                    .forEach(alert -> {
                        alert.setStatus("CLOSED");
                        alerts.save(alert);
                    });
        }

        Farm farm = farms.load(request.farmId());
        UUID alertId = null;
        if (outcome.equals("SICK") || outcome.equals("DEATH")) {
            String title = outcome.equals("DEATH")
                    ? "Animal deaths: " + request.animals() + " " + subject
                    : "Sick animals: " + label;
            String body = farm.getName() + " · " + request.animals() + " " + subject + " · " + request.notes().trim();
            Alert alert = new Alert();
            alert.setTenantId(access.tenantId());
            alert.setFarmId(request.farmId());
            alert.setSeverity(severity);
            alert.setTitle(clip(title, 160));
            alert.setBody(clip(body, 400));
            alert.setStatus("OPEN");
            alert.setSourceType(outcome);
            alert.setSourceId(healthCase.getId());
            alert.setOccurredAt(occurred.atStartOfDay(zone).toInstant());
            alerts.save(alert);
            alertId = alert.getId();
        }

        String action = outcome.equals("DEATH") ? "ANIMAL_DEATHS" : outcome.equals("SICK") ? "ANIMALS_SICK" : "ANIMALS_CURED";
        String activityTitle = outcome.equals("DEATH") ? "Deaths recorded" : outcome.equals("SICK") ? "Sick animals reported" : "Animals cured";
        String detail = request.animals() + " " + subject + (herdQuantity == null ? "" : " · herd now " + herdQuantity);
        audit.record(action, "HEALTH_CASE", healthCase.getId(), clip(detail, 500));
        audit.activity("LIVESTOCK", activityTitle, clip(detail, 300), request.farmId());
        return new AnimalHealthResponse(healthCase.getId(), alertId, farm.getId(), farm.getName(), outcome, request.animals(), herdQuantity);
    }

    @Transactional(readOnly = true)
    public SettingsResponse settings() {
        Tenant tenant = tenants.findById(access.tenantId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TENANT_NOT_FOUND", "Tenant not found"));
        return new SettingsResponse(
                tenant.getName(),
                tenant.getCode(),
                tenant.getCurrencyCode(),
                tenant.getTimezone(),
                tenant.getSeasonLabel(),
                tenant.getLocale(),
                tenant.getSubscriptionPlan(),
                tenant.getSubscriptionValidUntil()
        );
    }

    @Transactional
    public SettingsResponse updateSettings(SettingsUpdateRequest request) {
        if (!access.canManageAccess()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "You do not have permission to do that");
        }
        Tenant tenant = tenants.findById(access.tenantId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TENANT_NOT_FOUND", "Tenant not found"));
        String name = request.name().trim();
        String code = request.code().trim();
        String currency = request.currencyCode().trim().toUpperCase(Locale.ROOT);
        String timezone = request.timezone().trim();
        String season = request.seasonLabel().trim();
        String locale = request.locale().trim();
        if (!currency.matches("[A-Z]{3}")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CURRENCY_INVALID", "Currency must be a 3-letter code.");
        }
        try {
            ZoneId.of(timezone);
        } catch (DateTimeException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "TIMEZONE_INVALID", "Enter a timezone such as Africa/Dar_es_Salaam.");
        }
        if (tenants.existsByCodeAndIdNot(code, tenant.getId())) {
            throw new ApiException(HttpStatus.CONFLICT, "CODE_IN_USE", "That workspace code is already used.");
        }
        List<String> changes = new ArrayList<>();
        if (!name.equals(tenant.getName())) {
            changes.add("name");
        }
        if (!code.equals(tenant.getCode())) {
            changes.add("code");
        }
        if (!currency.equals(tenant.getCurrencyCode())) {
            changes.add("currency");
        }
        if (!timezone.equals(tenant.getTimezone())) {
            changes.add("timezone");
        }
        if (!season.equals(tenant.getSeasonLabel())) {
            changes.add("season");
        }
        if (!locale.equals(tenant.getLocale())) {
            changes.add("locale");
        }
        tenant.setName(name);
        tenant.setCode(code);
        tenant.setCurrencyCode(currency);
        tenant.setTimezone(timezone);
        tenant.setSeasonLabel(season);
        tenant.setLocale(locale);
        tenants.save(tenant);
        if (!changes.isEmpty()) {
            audit.record("SETTINGS_UPDATED", "TENANT", tenant.getId(), String.join(", ", changes));
        }
        return settings();
    }

    @Transactional(readOnly = true)
    public List<SearchHit> search(String q) {
        if (q == null || q.isBlank()) {
            return List.of();
        }
        String term = q.trim();
        List<SearchHit> hits = new ArrayList<>();
        farms.visibleFarms().stream()
                .filter(farm -> contains(farm.getName(), term) || contains(farm.getCode(), term) || contains(farm.getRegion(), term))
                .limit(6)
                .forEach(farm -> hits.add(new SearchHit("Farm", farm.getId(), farm.getName(), farm.getRegion())));
        animals.search(access.tenantId(), access.allFarms(), access.farmScope(), null, null, term,
                        org.springframework.data.domain.PageRequest.of(0, 6))
                .forEach(animal -> hits.add(new SearchHit("Animal", animal.getId(),
                        animal.getName() == null ? animal.getTag() : animal.getTag() + " · " + animal.getName(),
                        animal.getTag())));
        inventory.findTop8ByTenantIdAndNameContainingIgnoreCase(access.tenantId(), term).stream()
                .filter(item -> access.canReadFarm(item.getFarmId()))
                .limit(6)
                .forEach(item -> hits.add(new SearchHit("Inventory", item.getId(), item.getName(), item.getSku())));
        staff.findTop8ByTenantIdAndFullNameContainingIgnoreCase(access.tenantId(), term).stream()
                .filter(member -> access.canReadFarm(member.getFarmId()))
                .limit(6)
                .forEach(member -> hits.add(new SearchHit("Staff", member.getId(), member.getFullName(), member.getRoleTitle())));
        return hits;
    }

    public String dashboardCsv(com.shambanexa.dto.DashboardResponse dashboard) {
        access.requireAuthority("REPORT_EXPORT");
        StringBuilder csv = new StringBuilder();
        csv.append("measure,value\n");
        csv.append("generated_at,").append(dashboard.generatedAt()).append('\n');
        csv.append("total_animals,").append(dashboard.totalAnimals()).append('\n');
        csv.append("farms,").append(dashboard.farmCount()).append('\n');
        csv.append("milk_today_litres,").append(dashboard.milkTodayLitres()).append('\n');
        csv.append("revenue,").append(dashboard.revenue()).append('\n');
        csv.append("reconciled,").append(dashboard.reconciled()).append('\n');
        csv.append("\nspecies,count\n");
        dashboard.species().forEach(item -> csv.append(item.name()).append(',').append(item.count()).append('\n'));
        csv.append("\nfarm,region,animals\n");
        dashboard.locations().forEach(pin -> csv.append(pin.name()).append(',').append(pin.region()).append(',').append(pin.animalCount()).append('\n'));
        return csv.toString();
    }

    private int estimated(FishCycle cycle) {
        return cycle.getStockedQuantity() - cycle.getMortality() - cycle.getHarvested();
    }

    private Map<UUID, Farm> farmMap() {
        return farms.visibleFarms().stream().collect(Collectors.toMap(Farm::getId, Function.identity()));
    }

    private String farmName(Map<UUID, Farm> farmMap, UUID farmId) {
        Farm farm = farmMap.get(farmId);
        return farm == null ? "" : farm.getName();
    }

    private String clip(String value, int max) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }

    private String blank(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private boolean contains(String value, String term) {
        return value != null && value.toLowerCase().contains(term.toLowerCase());
    }
}
