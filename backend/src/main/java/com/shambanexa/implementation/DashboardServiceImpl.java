package com.shambanexa.implementation;

import com.shambanexa.service.DashboardService;
import com.shambanexa.service.PopulationService;
import com.shambanexa.service.FarmService;

import com.shambanexa.dto.ActivityResponse;
import com.shambanexa.dto.AlertResponse;
import com.shambanexa.dto.DashboardResponse;
import com.shambanexa.dto.FarmPin;
import com.shambanexa.dto.SpeciesCount;
import com.shambanexa.dto.SubscriptionView;
import com.shambanexa.dto.TrendPoint;
import com.shambanexa.common.ApiException;
import com.shambanexa.common.Percent;
import com.shambanexa.entity.ActivityEvent;
import com.shambanexa.entity.Alert;
import com.shambanexa.entity.Farm;
import com.shambanexa.entity.Species;
import com.shambanexa.entity.Tenant;
import com.shambanexa.repository.ActivityEventRepository;
import com.shambanexa.repository.AlertRepository;
import com.shambanexa.repository.FacilityRepository;
import com.shambanexa.repository.FinancialEntryRepository;
import com.shambanexa.repository.PopulationSnapshotRepository;
import com.shambanexa.repository.ProductionMonthRepository;
import com.shambanexa.repository.ProductionRecordRepository;
import com.shambanexa.repository.TenantRepository;
import com.shambanexa.security.AccessGuard;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class DashboardServiceImpl implements DashboardService {

    static final ZoneId BUSINESS_ZONE = ZoneId.of("Africa/Dar_es_Salaam");

    private final TenantRepository tenants;
    private final FarmService farms;
    private final FacilityRepository facilities;
    private final PopulationService population;
    private final PopulationSnapshotRepository snapshots;
    private final ProductionRecordRepository production;
    private final ProductionMonthRepository months;
    private final FinancialEntryRepository financials;
    private final AlertRepository alerts;
    private final ActivityEventRepository activity;
    private final com.shambanexa.repository.SpeciesRepository species;
    private final AccessGuard access;

    public DashboardServiceImpl(TenantRepository tenants, FarmService farms, FacilityRepository facilities,
                            PopulationService population, PopulationSnapshotRepository snapshots,
                            ProductionRecordRepository production, ProductionMonthRepository months,
                            FinancialEntryRepository financials, AlertRepository alerts,
                            ActivityEventRepository activity, com.shambanexa.repository.SpeciesRepository species,
                            AccessGuard access) {
        this.tenants = tenants;
        this.farms = farms;
        this.facilities = facilities;
        this.population = population;
        this.snapshots = snapshots;
        this.production = production;
        this.months = months;
        this.financials = financials;
        this.alerts = alerts;
        this.activity = activity;
        this.species = species;
        this.access = access;
    }

    @Transactional(readOnly = true)
    public DashboardResponse current() {
        Tenant tenant = tenants.findById(access.tenantId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TENANT_NOT_FOUND", "Tenant not found"));
        LocalDate today = LocalDate.now(BUSINESS_ZONE);
        List<Farm> visible = farms.visibleFarms();
        Map<UUID, Long> byFarm = population.byFarm();
        Map<UUID, Long> bySpecies = population.bySpecies();
        long totalAnimals = population.total();

        BigDecimal animalChange = null;
        if (access.allFarms()) {
            animalChange = snapshots
                    .findFirstByTenantIdAndAsOfDateLessThanOrderByAsOfDateDesc(tenant.getId(), today)
                    .map(snapshot -> Percent.change(totalAnimals, snapshot.getAnimalCount()))
                    .orElse(null);
        }

        LocalDate quarterStart = today.withMonth(((today.getMonthValue() - 1) / 3) * 3 + 1).withDayOfMonth(1);
        Instant quarterInstant = quarterStart.atStartOfDay(BUSINESS_ZONE).toInstant();
        long newFarms = visible.stream()
                .filter(farm -> farm.getCreatedAt() != null && !farm.getCreatedAt().isBefore(quarterInstant))
                .count();

        BigDecimal milkToday = production.sumMilk(tenant.getId(), today, access.allFarms(), access.farmScope());
        BigDecimal milkYesterday = production.sumMilk(tenant.getId(), today.minusDays(1), access.allFarms(), access.farmScope());
        BigDecimal milkDelta = milkToday.subtract(milkYesterday);

        LocalDate monthStart = today.withDayOfMonth(1);
        BigDecimal revenue = financials.sumIncome(tenant.getId(), monthStart, monthStart.plusMonths(1), access.allFarms(), access.farmScope());
        BigDecimal previousRevenue = financials.sumIncome(tenant.getId(), monthStart.minusMonths(1), monthStart, access.allFarms(), access.farmScope());

        int fieldCount = access.allFarms()
                ? (int) facilities.countByTenantId(tenant.getId())
                : (int) facilities.countByTenantIdAndFarmIdIn(tenant.getId(), access.farmScope());
        int regionCount = (int) visible.stream().map(Farm::getRegion).distinct().count();

        List<Species> catalog = species.findByTenantIdOrderBySortOrderAsc(tenant.getId());
        List<SpeciesCount> speciesCounts = catalog.stream()
                .filter(item -> "LIVESTOCK".equals(item.getCategory()))
                .map(item -> new SpeciesCount(item.getName(), bySpecies.getOrDefault(item.getId(), 0L), item.getChartColor()))
                .filter(item -> item.count() > 0)
                .toList();

        List<FarmPin> pins = visible.stream()
                .map(farm -> new FarmPin(
                        farm.getId(),
                        farm.getName(),
                        farm.getRegion(),
                        farm.getStatus(),
                        farm.getLatitude(),
                        farm.getLongitude(),
                        byFarm.getOrDefault(farm.getId(), 0L)
                ))
                .toList();

        List<TrendPoint> trends = months.findByTenantIdOrderByYearMonthAsc(tenant.getId()).stream()
                .map(month -> new TrendPoint(month.getYearMonth(), month.getMilkLitres(), month.getRevenueAmount()))
                .toList();

        List<AlertResponse> openAlerts = openAlerts();
        Map<UUID, Farm> farmMap = visible.stream()
                .collect(java.util.stream.Collectors.toMap(Farm::getId, farm -> farm));
        List<ActivityResponse> recent = activity.findTop20ByTenantIdOrderByOccurredAtDesc(tenant.getId()).stream()
                .filter(event -> event.getFarmId() == null || access.canReadFarm(event.getFarmId()))
                .limit(5)
                .map(event -> toActivity(event, farmMap))
                .toList();

        return new DashboardResponse(
                Instant.now(),
                tenant.getSeasonLabel(),
                tenant.getCurrencyCode(),
                totalAnimals,
                animalChange,
                visible.size(),
                newFarms,
                milkToday,
                milkDelta,
                revenue,
                Percent.change(revenue, previousRevenue),
                fieldCount,
                regionCount,
                population.reconciled(),
                speciesCounts,
                pins,
                trends,
                openAlerts.stream().limit(3).toList(),
                recent,
                new SubscriptionView(tenant.getSubscriptionPlan(), tenant.getSubscriptionValidUntil()),
                openAlerts.size()
        );
    }

    @Transactional(readOnly = true)
    public List<AlertResponse> openAlerts() {
        Map<UUID, String> farmNames = farms.visibleFarms().stream()
                .collect(java.util.stream.Collectors.toMap(Farm::getId, Farm::getName));
        return alerts.findByTenantIdAndStatusOrderByOccurredAtDesc(access.tenantId(), "OPEN").stream()
                .filter(alert -> alert.getFarmId() == null || access.canReadFarm(alert.getFarmId()))
                .map(alert -> toAlert(alert, farmNames))
                .toList();
    }

    private AlertResponse toAlert(Alert alert, Map<UUID, String> farmNames) {
        return new AlertResponse(
                alert.getId(),
                alert.getSeverity(),
                alert.getTitle(),
                alert.getBody(),
                alert.getStatus(),
                alert.getFarmId(),
                alert.getFarmId() == null ? null : farmNames.get(alert.getFarmId()),
                alert.getOccurredAt(),
                alert.getSourceType()
        );
    }

    @Transactional(readOnly = true)
    public List<ActivityResponse> activity() {
        Map<UUID, Farm> farmMap = farms.visibleFarms().stream()
                .collect(java.util.stream.Collectors.toMap(Farm::getId, farm -> farm));
        return activity.findByTenantIdOrderByOccurredAtDesc(access.tenantId()).stream()
                .filter(event -> event.getFarmId() == null || access.canReadFarm(event.getFarmId()))
                .map(event -> toActivity(event, farmMap))
                .toList();
    }

    private ActivityResponse toActivity(ActivityEvent event, Map<UUID, Farm> farmMap) {
        Farm farm = event.getFarmId() == null ? null : farmMap.get(event.getFarmId());
        return new ActivityResponse(
                event.getId(),
                event.getEventType(),
                event.getTitle(),
                event.getDetail(),
                event.getFarmId(),
                event.getOccurredAt(),
                farm == null ? null : farm.getName(),
                farm == null ? null : farm.getCode()
        );
    }
}
