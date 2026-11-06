package com.shambanexa.implementation;

import com.shambanexa.common.ApiException;
import com.shambanexa.dto.HealthCenterDtos.Board;
import com.shambanexa.dto.HealthCenterDtos.DoseBody;
import com.shambanexa.dto.HealthCenterDtos.DoseView;
import com.shambanexa.dto.HealthCenterDtos.MedicineUse;
import com.shambanexa.dto.HealthCenterDtos.NamedCount;
import com.shambanexa.dto.HealthCenterDtos.ProtocolActiveBody;
import com.shambanexa.dto.HealthCenterDtos.ProtocolBody;
import com.shambanexa.dto.HealthCenterDtos.ProtocolView;
import com.shambanexa.dto.HealthCenterDtos.Report;
import com.shambanexa.dto.HealthCenterDtos.TreatmentBody;
import com.shambanexa.dto.HealthCenterDtos.TreatmentStatusBody;
import com.shambanexa.dto.HealthCenterDtos.TreatmentView;
import com.shambanexa.dto.HealthCenterDtos.VaccinationBody;
import com.shambanexa.dto.HealthCenterDtos.VaccinationStatusBody;
import com.shambanexa.dto.HealthCenterDtos.VaccinationView;
import com.shambanexa.dto.StockIssueRequest;
import com.shambanexa.entity.Animal;
import com.shambanexa.entity.AnimalTreatment;
import com.shambanexa.entity.AnimalVaccination;
import com.shambanexa.entity.Farm;
import com.shambanexa.entity.HerdBatch;
import com.shambanexa.entity.InventoryBatch;
import com.shambanexa.entity.InventoryItem;
import com.shambanexa.entity.Species;
import com.shambanexa.entity.TreatmentDose;
import com.shambanexa.entity.VaccinationProtocol;
import com.shambanexa.repository.AnimalRepository;
import com.shambanexa.repository.AnimalTreatmentRepository;
import com.shambanexa.repository.AnimalVaccinationRepository;
import com.shambanexa.repository.FarmRepository;
import com.shambanexa.repository.HerdBatchRepository;
import com.shambanexa.repository.InventoryBatchRepository;
import com.shambanexa.repository.InventoryItemRepository;
import com.shambanexa.repository.SpeciesRepository;
import com.shambanexa.repository.TreatmentDoseRepository;
import com.shambanexa.repository.VaccinationProtocolRepository;
import com.shambanexa.security.AccessGuard;
import com.shambanexa.service.AuditTrail;
import com.shambanexa.service.InventoryService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class HealthCenterService {

    static final ZoneId ZONE = InventoryBatchBook.ZONE;
    private static final int DUE_WINDOW_DAYS = 30;
    private static final Set<String> TREATMENT_STATUS = Set.of("PLANNED", "IN_PROGRESS", "COMPLETED", "CANCELLED");
    private static final Set<String> VACCINATION_STATUS = Set.of("PLANNED", "COMPLETED", "MISSED", "CANCELLED");
    private static final String REMINDER = "Reminders stay in this workspace. Email is not configured.";

    private final AccessGuard access;
    private final AuditTrail audit;
    private final InventoryService inventory;
    private final AnimalTreatmentRepository treatments;
    private final TreatmentDoseRepository doses;
    private final VaccinationProtocolRepository protocols;
    private final AnimalVaccinationRepository vaccinations;
    private final AnimalRepository animals;
    private final HerdBatchRepository herds;
    private final FarmRepository farms;
    private final SpeciesRepository species;
    private final InventoryItemRepository items;
    private final InventoryBatchRepository batches;

    public HealthCenterService(
            AccessGuard access,
            AuditTrail audit,
            InventoryService inventory,
            AnimalTreatmentRepository treatments,
            TreatmentDoseRepository doses,
            VaccinationProtocolRepository protocols,
            AnimalVaccinationRepository vaccinations,
            AnimalRepository animals,
            HerdBatchRepository herds,
            FarmRepository farms,
            SpeciesRepository species,
            InventoryItemRepository items,
            InventoryBatchRepository batches
    ) {
        this.access = access;
        this.audit = audit;
        this.inventory = inventory;
        this.treatments = treatments;
        this.doses = doses;
        this.protocols = protocols;
        this.vaccinations = vaccinations;
        this.animals = animals;
        this.herds = herds;
        this.farms = farms;
        this.species = species;
        this.items = items;
        this.batches = batches;
    }

    @Transactional(readOnly = true)
    public Board board() {
        access.requireAuthority("ANIMAL_READ");
        Lookup lookup = lookup();
        LocalDate today = LocalDate.now(ZONE);
        List<TreatmentView> treatmentViews = treatments.findByTenantIdOrderByStartedOnDesc(access.tenantId()).stream()
                .filter(row -> access.canReadFarm(row.getFarmId()))
                .map(row -> treatmentView(row, lookup))
                .toList();
        List<VaccinationView> vaccinationViews = vaccinations.findByTenantIdOrderByCreatedAtDesc(access.tenantId()).stream()
                .filter(row -> access.canReadFarm(row.getFarmId()))
                .map(row -> vaccinationView(row, lookup, today))
                .toList();
        List<ProtocolView> protocolViews = protocols.findByTenantIdOrderByNameAsc(access.tenantId()).stream()
                .map(row -> protocolView(row, lookup))
                .toList();
        return new Board(
                treatmentViews,
                vaccinationViews,
                protocolViews,
                report(treatmentViews, vaccinationViews),
                access.current().authorities().contains("ANIMAL_WRITE"),
                canApproveProtocols()
        );
    }

    @Transactional
    public TreatmentView recordTreatment(TreatmentBody body) {
        access.requireAuthority("ANIMAL_WRITE");
        if (body == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "TREATMENT", "Enter the treatment");
        }
        Lookup lookup = lookup();
        Subject subject = subject(body.farmId(), body.animalId(), body.herdId(), lookup);
        String status = status(body.status(), TREATMENT_STATUS, "PLANNED");
        LocalDate started = requiredDate(body.startedOn(), "Enter the start date");
        LocalDate followUp = body.followUpOn();
        if (followUp != null && followUp.isBefore(started)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FOLLOW_UP", "The follow-up date is before the start date");
        }
        if ("CANCELLED".equals(status) && body.dose() != null && body.dose().itemId() != null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "DOSE", "A cancelled treatment does not issue medicine");
        }
        AnimalTreatment row = new AnimalTreatment();
        row.setTenantId(access.tenantId());
        row.setFarmId(subject.farmId());
        row.setAnimalId(subject.animalId());
        row.setHerdId(subject.herdId());
        row.setSymptoms(required(body.symptoms(), 2, 500, "Enter the symptoms"));
        row.setDiagnosis(required(body.diagnosis(), 2, 240, "Enter the diagnosis"));
        row.setDescription(required(body.description(), 2, 800, "Enter the treatment"));
        row.setVeterinarianName(required(body.veterinarianName(), 2, 120, "Enter the veterinarian"));
        row.setTreatmentStatus(status);
        row.setStartedOn(started);
        row.setOutcome(optional(body.outcome(), 240));
        row.setFollowUpOn(followUp);
        row.setCreatedBy(access.current().id());
        row.setCreatedAt(Instant.now());
        treatments.save(row);
        if (body.dose() != null && body.dose().itemId() != null) {
            saveDose(row, body.dose(), consumes(status), lookup);
        }
        audit.record("TREATMENT_RECORDED", "TREATMENT", row.getId(), row.getDiagnosis());
        audit.activity("TREATMENT", "Treatment recorded", subject.label() + " · " + row.getDiagnosis(), row.getFarmId());
        return treatmentView(row, lookup());
    }

    @Transactional
    public TreatmentView updateTreatment(UUID id, TreatmentStatusBody body) {
        access.requireAuthority("ANIMAL_WRITE");
        AnimalTreatment row = treatments.findByIdAndTenantId(id, access.tenantId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TREATMENT_NOT_FOUND", "Treatment not found"));
        access.checkFarm(row.getFarmId());
        if (body == null || blank(body.status()) == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "STATUS", "Choose a treatment status");
        }
        String status = status(body.status(), TREATMENT_STATUS, null);
        List<TreatmentDose> lines = doses.findByTreatmentId(row.getId());
        boolean issued = lines.stream().anyMatch(TreatmentDose::isIssued);
        if ("CANCELLED".equals(status) && issued) {
            throw new ApiException(HttpStatus.CONFLICT, "DOSE_ISSUED", "This treatment already issued medicine. Record a stock receipt if the dose was not used.");
        }
        if (body.followUpOn() != null && body.followUpOn().isBefore(row.getStartedOn())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FOLLOW_UP", "The follow-up date is before the start date");
        }
        row.setTreatmentStatus(status);
        if (body.outcome() != null) {
            row.setOutcome(optional(body.outcome(), 240));
        }
        if (body.followUpOn() != null) {
            row.setFollowUpOn(body.followUpOn());
        }
        treatments.save(row);
        if (consumes(status)) {
            for (TreatmentDose line : lines) {
                if (!line.isIssued()) {
                    issueDose(row, line);
                }
            }
        }
        audit.record("TREATMENT_UPDATED", "TREATMENT", row.getId(), status);
        audit.activity("TREATMENT", "Treatment updated", row.getDiagnosis() + " · " + label(status), row.getFarmId());
        return treatmentView(row, lookup());
    }

    @Transactional
    public ProtocolView saveProtocol(ProtocolBody body) {
        if (!canApproveProtocols()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "A Farm Adviser or super admin approves a vaccination protocol");
        }
        if (body == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PROTOCOL", "Enter the protocol");
        }
        Species kind = species.findByTenantIdAndNameIgnoreCase(access.tenantId(), blank(body.speciesName()) == null ? "" : body.speciesName().trim())
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "SPECIES", "Choose a species from master data"));
        if (body.intervalDays() == null || body.intervalDays() < 1 || body.intervalDays() > 3650) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INTERVAL", "Enter an interval between 1 and 3650 days");
        }
        VaccinationProtocol row = new VaccinationProtocol();
        row.setTenantId(access.tenantId());
        row.setSpeciesName(kind.getName());
        row.setName(required(body.name(), 2, 160, "Enter the protocol name"));
        row.setAgeGroup(required(body.ageGroup(), 2, 80, "Enter the age group"));
        row.setIntervalDays(body.intervalDays());
        if (body.itemId() != null) {
            InventoryItem item = medicine(body.itemId());
            access.checkFarm(item.getFarmId());
            row.setItemId(item.getId());
        }
        row.setNotes(optional(body.notes(), 400));
        row.setApprovedBy(access.current().id());
        row.setApprovedByName(access.current().fullName());
        row.setActive(true);
        row.setCreatedAt(Instant.now());
        protocols.save(row);
        audit.record("PROTOCOL_APPROVED", "PROTOCOL", row.getId(), row.getName());
        audit.activity("PROTOCOL", "Vaccination protocol approved", row.getSpeciesName() + " · " + row.getName(), null);
        return protocolView(row, lookup());
    }

    @Transactional
    public ProtocolView setProtocolActive(UUID id, ProtocolActiveBody body) {
        if (!canApproveProtocols()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "A Farm Adviser or super admin changes a vaccination protocol");
        }
        VaccinationProtocol row = protocols.findByIdAndTenantId(id, access.tenantId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PROTOCOL_NOT_FOUND", "Protocol not found"));
        row.setActive(body != null && body.active());
        protocols.save(row);
        audit.record("PROTOCOL_UPDATED", "PROTOCOL", row.getId(), row.isActive() ? "ACTIVE" : "INACTIVE");
        return protocolView(row, lookup());
    }

    @Transactional
    public VaccinationView recordVaccination(VaccinationBody body) {
        access.requireAuthority("ANIMAL_WRITE");
        if (body == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VACCINATION", "Enter the vaccination");
        }
        Lookup lookup = lookup();
        Subject subject = subject(body.farmId(), body.animalId(), body.herdId(), lookup);
        String status = status(body.status(), VACCINATION_STATUS, "PLANNED");
        VaccinationProtocol protocol = protocol(body.protocolId());
        String speciesName = required(body.speciesName(), 2, 80, "Choose a species");
        species.findByTenantIdAndNameIgnoreCase(access.tenantId(), speciesName)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "SPECIES", "Choose a species from master data"));
        if (protocol != null && !protocol.getSpeciesName().equalsIgnoreCase(speciesName)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SPECIES", "This protocol is for " + protocol.getSpeciesName());
        }
        LocalDate administered = body.administeredOn();
        LocalDate nextDue = body.nextDueOn();
        if ("COMPLETED".equals(status) && administered == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "DATE", "Enter the date the vaccine was given");
        }
        if (("PLANNED".equals(status) || "MISSED".equals(status)) && nextDue == null && administered == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "DATE", "Enter the next due date");
        }
        if (nextDue == null && protocol != null && administered != null) {
            nextDue = administered.plusDays(protocol.getIntervalDays());
        }
        if ("CANCELLED".equals(status) && body.itemId() != null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "DOSE", "A cancelled vaccination does not issue stock");
        }
        AnimalVaccination row = new AnimalVaccination();
        row.setTenantId(access.tenantId());
        row.setFarmId(subject.farmId());
        row.setAnimalId(subject.animalId());
        row.setHerdId(subject.herdId());
        row.setProtocolId(protocol == null ? null : protocol.getId());
        row.setSpeciesName(species.findByTenantIdAndNameIgnoreCase(access.tenantId(), speciesName).orElseThrow().getName());
        row.setAgeGroup(protocol != null && blank(body.ageGroup()) == null ? protocol.getAgeGroup() : optional(body.ageGroup(), 80));
        row.setVaccineName(protocol != null && blank(body.vaccineName()) == null ? protocol.getName() : required(body.vaccineName(), 2, 160, "Enter the vaccine name"));
        row.setAdministeredOn(administered);
        row.setNextDueOn(nextDue);
        row.setVeterinarianName(required(body.veterinarianName(), 2, 120, "Enter the veterinarian"));
        row.setNotes(optional(body.notes(), 400));
        row.setVaccinationStatus(status);
        row.setDoseIssued(false);
        row.setCreatedBy(access.current().id());
        row.setCreatedAt(Instant.now());
        if (body.itemId() != null) {
            PreparedDose prepared = prepare(subject.farmId(), body.itemId(), body.batchId(), body.quantity(), body.withdrawalDays(), body.overrideReason(), "Vaccination");
            row.setItemId(prepared.item().getId());
            row.setBatchId(prepared.batch().getId());
            row.setQuantity(prepared.quantity());
            row.setWithdrawalDays(prepared.withdrawalDays());
            if ("COMPLETED".equals(status)) {
                inventory.issue(prepared.item().getId(), prepared.request());
                row.setDoseIssued(true);
                row.setWithdrawalEndsOn(prepared.endsOn(LocalDate.now(ZONE)));
            }
        }
        vaccinations.save(row);
        audit.record("VACCINATION_RECORDED", "VACCINATION", row.getId(), row.getVaccineName());
        audit.activity("VACCINATION", "Vaccination recorded", subject.label() + " · " + row.getVaccineName(), row.getFarmId());
        return vaccinationView(row, lookup(), LocalDate.now(ZONE));
    }

    @Transactional
    public VaccinationView updateVaccination(UUID id, VaccinationStatusBody body) {
        access.requireAuthority("ANIMAL_WRITE");
        AnimalVaccination row = vaccinations.findByIdAndTenantId(id, access.tenantId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "VACCINATION_NOT_FOUND", "Vaccination not found"));
        access.checkFarm(row.getFarmId());
        if (body == null || blank(body.status()) == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "STATUS", "Choose a vaccination status");
        }
        String status = status(body.status(), VACCINATION_STATUS, null);
        if ("CANCELLED".equals(status) && row.isDoseIssued()) {
            throw new ApiException(HttpStatus.CONFLICT, "DOSE_ISSUED", "This vaccination already issued stock. Record a stock receipt if the dose was not used.");
        }
        if (body.administeredOn() != null) {
            row.setAdministeredOn(body.administeredOn());
        }
        if (body.nextDueOn() != null) {
            row.setNextDueOn(body.nextDueOn());
        }
        if ("COMPLETED".equals(status) && row.getAdministeredOn() == null) {
            row.setAdministeredOn(LocalDate.now(ZONE));
        }
        if ("COMPLETED".equals(status) && row.getItemId() != null && !row.isDoseIssued()) {
            InventoryItem item = medicine(row.getItemId());
            if (!item.getFarmId().equals(row.getFarmId())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "FARM", "The selected medicine is not stocked on this farm");
            }
            InventoryBatch batch = chooseBatch(item, row.getBatchId(), null);
            inventory.issue(item.getId(), new StockIssueRequest(row.getQuantity(), "Vaccination", batch.getId(), null));
            row.setBatchId(batch.getId());
            row.setDoseIssued(true);
            if (row.getWithdrawalDays() != null) {
                row.setWithdrawalEndsOn(LocalDate.now(ZONE).plusDays(row.getWithdrawalDays()));
            }
        }
        row.setVaccinationStatus(status);
        vaccinations.save(row);
        audit.record("VACCINATION_UPDATED", "VACCINATION", row.getId(), status);
        audit.activity("VACCINATION", "Vaccination updated", row.getVaccineName() + " · " + label(status), row.getFarmId());
        return vaccinationView(row, lookup(), LocalDate.now(ZONE));
    }

    private void saveDose(AnimalTreatment treatment, DoseBody body, boolean issueNow, Lookup lookup) {
        PreparedDose prepared = prepare(treatment.getFarmId(), body.itemId(), body.batchId(), body.quantity(), body.withdrawalDays(), body.overrideReason(), "Animal treatment");
        TreatmentDose line = new TreatmentDose();
        line.setTenantId(access.tenantId());
        line.setTreatmentId(treatment.getId());
        line.setItemId(prepared.item().getId());
        line.setBatchId(prepared.batch().getId());
        line.setQuantity(prepared.quantity());
        line.setWithdrawalDays(prepared.withdrawalDays());
        line.setIssued(false);
        line.setCreatedAt(Instant.now());
        if (issueNow) {
            inventory.issue(prepared.item().getId(), prepared.request());
            line.setIssued(true);
            line.setWithdrawalEndsOn(prepared.endsOn(LocalDate.now(ZONE)));
        }
        doses.save(line);
        lookup.doses().put(treatment.getId(), line);
    }

    private void issueDose(AnimalTreatment treatment, TreatmentDose line) {
        InventoryItem item = medicine(line.getItemId());
        if (!item.getFarmId().equals(treatment.getFarmId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FARM", "The selected medicine is not stocked on this farm");
        }
        InventoryBatch batch = chooseBatch(item, line.getBatchId(), null);
        inventory.issue(item.getId(), new StockIssueRequest(line.getQuantity(), "Animal treatment", batch.getId(), null));
        line.setBatchId(batch.getId());
        line.setIssued(true);
        if (line.getWithdrawalDays() != null) {
            line.setWithdrawalEndsOn(LocalDate.now(ZONE).plusDays(line.getWithdrawalDays()));
        }
        doses.save(line);
    }

    private PreparedDose prepare(UUID farmId, UUID itemId, UUID batchId, BigDecimal quantity, Integer withdrawalDays, String overrideReason, String reason) {
        if (quantity == null || quantity.signum() <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "QUANTITY", "Enter the quantity administered");
        }
        InventoryItem item = medicine(itemId);
        if (!item.getFarmId().equals(farmId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FARM", "The selected medicine is not stocked on this farm");
        }
        access.checkFarm(item.getFarmId());
        BigDecimal amount = quantity.setScale(3, RoundingMode.HALF_UP);
        InventoryBatch batch = chooseBatch(item, batchId, overrideReason);
        Integer days = withdrawalDays != null ? withdrawalDays : item.getWithdrawalDays();
        if (days != null && (days < 0 || days > 3650)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "WITHDRAWAL", "Withdrawal days must be between 0 and 3650");
        }
        String override = blank(overrideReason);
        return new PreparedDose(item, batch, amount, days, new StockIssueRequest(amount, reason, batch.getId(), override));
    }

    private InventoryBatch chooseBatch(InventoryItem item, UUID batchId, String overrideReason) {
        LocalDate today = LocalDate.now(ZONE);
        List<InventoryBatch> rows = batches.findByTenantIdAndItemIdOrderByExpiresOnAsc(item.getTenantId(), item.getId());
        InventoryBatch earliest = InventoryExpiry.earliest(rows, today);
        if (earliest == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "NO_USABLE_BATCH", "No open batch is eligible to issue. Expired, quarantined, and disposed stock stays on the record.");
        }
        InventoryBatch chosen = batchId == null
                ? earliest
                : batches.findByIdAndTenantId(batchId, item.getTenantId()).orElse(null);
        if (chosen == null || !chosen.getItemId().equals(item.getId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "BATCH_NOT_FOUND", "Choose a batch of this product");
        }
        if (!InventoryExpiry.usable(chosen, today) || chosen.getQuantityRemaining().signum() <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "BATCH_BLOCKED", "Expired, quarantined, and disposed batches cannot be administered");
        }
        if (!chosen.getId().equals(earliest.getId()) && blank(overrideReason) == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FEFO_OVERRIDE", "The earliest eligible batch is " + earliest.getLotCode() + ". Record a reason to use a different batch.");
        }
        return chosen;
    }

    private InventoryItem medicine(UUID id) {
        InventoryItem item = items.findByIdAndTenantId(id, access.tenantId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ITEM_NOT_FOUND", "Medicine not found"));
        if (!"MEDICINE".equals(item.getCategory())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "MEDICINE", "Choose a medicine from inventory");
        }
        return item;
    }

    private VaccinationProtocol protocol(UUID id) {
        if (id == null) {
            return null;
        }
        VaccinationProtocol row = protocols.findByIdAndTenantId(id, access.tenantId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PROTOCOL_NOT_FOUND", "Protocol not found"));
        if (!row.isActive()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PROTOCOL", "This protocol is not active");
        }
        return row;
    }

    private Subject subject(UUID farmId, UUID animalId, UUID herdId, Lookup lookup) {
        if (farmId == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FARM", "Choose a farm");
        }
        access.checkFarm(farmId);
        if (!lookup.farms().containsKey(farmId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "FARM_NOT_FOUND", "Farm not found");
        }
        if (animalId == null && herdId == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SUBJECT", "Choose an animal or a herd");
        }
        if (animalId != null && herdId != null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SUBJECT", "Choose either an animal or a herd");
        }
        if (animalId != null) {
            Animal animal = lookup.animals().get(animalId);
            if (animal == null || !animal.getFarmId().equals(farmId)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "ANIMAL", "Choose an animal on this farm");
            }
            return new Subject(farmId, animalId, null, animalLabel(animal));
        }
        HerdBatch herd = lookup.herds().get(herdId);
        if (herd == null || !herd.getFarmId().equals(farmId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "HERD", "Choose a herd on this farm");
        }
        return new Subject(farmId, null, herdId, herd.getName());
    }

    private TreatmentView treatmentView(AnimalTreatment row, Lookup lookup) {
        TreatmentDose dose = lookup.doses().get(row.getId());
        return new TreatmentView(
                row.getId(),
                row.getFarmId(),
                farmName(lookup, row.getFarmId()),
                row.getAnimalId(),
                row.getHerdId(),
                subjectLabel(lookup, row.getAnimalId(), row.getHerdId()),
                speciesOf(lookup, row.getAnimalId(), row.getHerdId()),
                row.getSymptoms(),
                row.getDiagnosis(),
                row.getDescription(),
                row.getVeterinarianName(),
                row.getTreatmentStatus(),
                row.getStartedOn(),
                row.getOutcome(),
                row.getFollowUpOn(),
                dose == null ? null : doseView(dose, lookup)
        );
    }

    private DoseView doseView(TreatmentDose dose, Lookup lookup) {
        InventoryItem item = lookup.items().get(dose.getItemId());
        InventoryBatch batch = dose.getBatchId() == null ? null : lookup.batches().get(dose.getBatchId());
        return new DoseView(
                dose.getItemId(),
                item == null ? "Medicine" : item.getName(),
                item == null ? "" : item.getUnit(),
                dose.getBatchId(),
                batch == null ? null : batch.getLotCode(),
                dose.getQuantity(),
                dose.getWithdrawalDays(),
                dose.getWithdrawalEndsOn(),
                dose.isIssued(),
                cost(dose.getQuantity(), batch, item)
        );
    }

    private ProtocolView protocolView(VaccinationProtocol row, Lookup lookup) {
        InventoryItem item = row.getItemId() == null ? null : lookup.items().get(row.getItemId());
        String itemName = null;
        if (item != null && access.canReadFarm(item.getFarmId())) {
            itemName = item.getName();
        } else if (item != null) {
            itemName = "A medicine on another farm";
        }
        return new ProtocolView(
                row.getId(),
                row.getSpeciesName(),
                row.getName(),
                row.getAgeGroup(),
                row.getIntervalDays(),
                row.getItemId(),
                itemName,
                row.getNotes(),
                row.getApprovedByName(),
                row.isActive(),
                row.getCreatedAt().atZone(ZONE).toLocalDate()
        );
    }

    private VaccinationView vaccinationView(AnimalVaccination row, Lookup lookup, LocalDate today) {
        InventoryItem item = row.getItemId() == null ? null : lookup.items().get(row.getItemId());
        InventoryBatch batch = row.getBatchId() == null ? null : lookup.batches().get(row.getBatchId());
        VaccinationProtocol protocol = row.getProtocolId() == null ? null : lookup.protocols().get(row.getProtocolId());
        return new VaccinationView(
                row.getId(),
                row.getFarmId(),
                farmName(lookup, row.getFarmId()),
                row.getAnimalId(),
                row.getHerdId(),
                subjectLabel(lookup, row.getAnimalId(), row.getHerdId()),
                row.getProtocolId(),
                protocol == null ? null : protocol.getName(),
                row.getSpeciesName(),
                row.getAgeGroup(),
                row.getVaccineName(),
                row.getItemId(),
                item == null ? null : item.getName(),
                row.getBatchId(),
                batch == null ? null : batch.getLotCode(),
                row.getQuantity(),
                item == null ? null : item.getUnit(),
                row.getWithdrawalDays(),
                row.getWithdrawalEndsOn(),
                row.isDoseIssued(),
                row.isDoseIssued() ? cost(row.getQuantity(), batch, item) : null,
                row.getAdministeredOn(),
                row.getNextDueOn(),
                row.getVeterinarianName(),
                row.getNotes(),
                row.getVaccinationStatus(),
                displayStatus(row, today)
        );
    }

    private Report report(List<TreatmentView> treatments, List<VaccinationView> vaccinations) {
        long inProgress = treatments.stream().filter(row -> "IN_PROGRESS".equals(row.status())).count();
        long completed = treatments.stream().filter(row -> "COMPLETED".equals(row.status())).count();
        long planned = vaccinations.stream().filter(row -> "PLANNED".equals(row.status()) && !"OVERDUE".equals(row.displayStatus())).count();
        long overdue = vaccinations.stream().filter(row -> "OVERDUE".equals(row.displayStatus())).count();
        long missed = vaccinations.stream().filter(row -> "MISSED".equals(row.status())).count();
        long done = vaccinations.stream().filter(row -> "COMPLETED".equals(row.status())).count();
        LocalDate today = LocalDate.now(ZONE);
        LocalDate soon = today.plusDays(DUE_WINDOW_DAYS);
        long dueSoon = vaccinations.stream()
                .filter(row -> "PLANNED".equals(row.status()) && row.nextDueOn() != null && !row.nextDueOn().isBefore(today) && !row.nextDueOn().isAfter(soon))
                .count();
        Map<String, Long> conditions = new LinkedHashMap<>();
        Map<String, Long> vets = new LinkedHashMap<>();
        BigDecimal known = BigDecimal.ZERO;
        int missingCost = 0;
        Map<String, MedicineUse> medicines = new LinkedHashMap<>();
        for (TreatmentView row : treatments) {
            if (!"CANCELLED".equals(row.status())) {
                conditions.merge(row.diagnosis(), 1L, Long::sum);
                vets.merge(row.veterinarianName(), 1L, Long::sum);
            }
            if (row.dose() != null && row.dose().issued()) {
                if (row.dose().knownCost() == null) {
                    missingCost++;
                } else {
                    known = known.add(row.dose().knownCost());
                }
                addUse(medicines, row.dose().itemName(), row.dose().unit(), row.dose().quantity(), row.dose().knownCost());
            }
        }
        for (VaccinationView row : vaccinations) {
            if (!"CANCELLED".equals(row.status())) {
                vets.merge(row.veterinarianName(), 1L, Long::sum);
            }
            if (row.doseIssued() && row.quantity() != null) {
                if (row.knownCost() == null) {
                    missingCost++;
                } else {
                    known = known.add(row.knownCost());
                }
                addUse(medicines, row.itemName() == null ? row.vaccineName() : row.itemName(), row.unit() == null ? "" : row.unit(), row.quantity(), row.knownCost());
            }
        }
        return new Report(
                inProgress,
                completed,
                planned,
                dueSoon,
                overdue,
                missed,
                done,
                known.signum() == 0 ? null : known.setScale(2, RoundingMode.HALF_UP),
                missingCost,
                counts(conditions),
                List.copyOf(medicines.values()),
                counts(vets),
                REMINDER
        );
    }

    private void addUse(Map<String, MedicineUse> medicines, String name, String unit, BigDecimal quantity, BigDecimal cost) {
        MedicineUse current = medicines.get(name);
        BigDecimal nextQty = quantity.add(current == null ? BigDecimal.ZERO : current.quantity());
        BigDecimal nextCost = cost == null ? (current == null ? null : current.knownCost()) : cost.add(current == null || current.knownCost() == null ? BigDecimal.ZERO : current.knownCost());
        medicines.put(name, new MedicineUse(name, unit, nextQty, nextCost == null ? null : nextCost.setScale(2, RoundingMode.HALF_UP)));
    }

    private List<NamedCount> counts(Map<String, Long> values) {
        return values.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(entry -> new NamedCount(entry.getKey(), entry.getValue()))
                .toList();
    }

    private BigDecimal cost(BigDecimal quantity, InventoryBatch batch, InventoryItem item) {
        BigDecimal unit = batch != null && batch.getUnitCost() != null ? batch.getUnitCost() : item == null ? null : item.getUnitCost();
        if (unit == null || quantity == null) {
            return null;
        }
        return quantity.multiply(unit).setScale(2, RoundingMode.HALF_UP);
    }

    private String displayStatus(AnimalVaccination row, LocalDate today) {
        if ("PLANNED".equals(row.getVaccinationStatus()) && row.getNextDueOn() != null && row.getNextDueOn().isBefore(today)) {
            return "OVERDUE";
        }
        return row.getVaccinationStatus();
    }

    private Lookup lookup() {
        UUID tenantId = access.tenantId();
        Map<UUID, Farm> farmMap = new LinkedHashMap<>();
        farms.findAll().stream().filter(farm -> tenantId.equals(farm.getTenantId())).forEach(farm -> farmMap.put(farm.getId(), farm));
        Map<UUID, Animal> animalMap = new LinkedHashMap<>();
        animals.findAll().stream().filter(animal -> tenantId.equals(animal.getTenantId())).forEach(animal -> animalMap.put(animal.getId(), animal));
        Map<UUID, HerdBatch> herdMap = new LinkedHashMap<>();
        herds.findAll().stream().filter(herd -> tenantId.equals(herd.getTenantId())).forEach(herd -> herdMap.put(herd.getId(), herd));
        Map<UUID, Species> speciesMap = new LinkedHashMap<>();
        species.findByTenantIdOrderBySortOrderAsc(tenantId).forEach(row -> speciesMap.put(row.getId(), row));
        Map<UUID, InventoryItem> itemMap = new LinkedHashMap<>();
        items.findAll().stream().filter(item -> tenantId.equals(item.getTenantId())).forEach(item -> itemMap.put(item.getId(), item));
        Map<UUID, InventoryBatch> batchMap = new LinkedHashMap<>();
        batches.findByTenantIdOrderByExpiresOnAsc(tenantId).forEach(batch -> batchMap.put(batch.getId(), batch));
        Map<UUID, TreatmentDose> doseMap = new LinkedHashMap<>();
        doses.findByTenantId(tenantId).forEach(dose -> doseMap.put(dose.getTreatmentId(), dose));
        Map<UUID, VaccinationProtocol> protocolMap = new LinkedHashMap<>();
        protocols.findByTenantIdOrderByNameAsc(tenantId).forEach(row -> protocolMap.put(row.getId(), row));
        return new Lookup(farmMap, animalMap, herdMap, speciesMap, itemMap, batchMap, doseMap, protocolMap);
    }

    private String subjectLabel(Lookup lookup, UUID animalId, UUID herdId) {
        if (animalId != null) {
            Animal animal = lookup.animals().get(animalId);
            return animal == null ? "Animal" : animalLabel(animal);
        }
        HerdBatch herd = lookup.herds().get(herdId);
        return herd == null ? "Herd" : herd.getName();
    }

    private String speciesOf(Lookup lookup, UUID animalId, UUID herdId) {
        UUID speciesId = null;
        if (animalId != null && lookup.animals().get(animalId) != null) {
            speciesId = lookup.animals().get(animalId).getSpeciesId();
        } else if (herdId != null && lookup.herds().get(herdId) != null) {
            speciesId = lookup.herds().get(herdId).getSpeciesId();
        }
        Species kind = speciesId == null ? null : lookup.species().get(speciesId);
        return kind == null ? "" : kind.getName();
    }

    private String farmName(Lookup lookup, UUID farmId) {
        Farm farm = lookup.farms().get(farmId);
        return farm == null ? "" : farm.getName();
    }

    private String animalLabel(Animal animal) {
        if (animal.getName() == null || animal.getName().isBlank()) {
            return animal.getTag();
        }
        return animal.getTag() + " · " + animal.getName();
    }

    private boolean canApproveProtocols() {
        return access.current().canAdvise() || access.current().canManageAccess();
    }

    private boolean consumes(String status) {
        return "IN_PROGRESS".equals(status) || "COMPLETED".equals(status);
    }

    private String status(String value, Set<String> allowed, String fallback) {
        String token = blank(value);
        if (token == null) {
            if (fallback == null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "STATUS", "Choose a status");
            }
            return fallback;
        }
        String normalized = token.toUpperCase(Locale.ROOT).replace(' ', '_');
        if (!allowed.contains(normalized)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "STATUS", "Choose a valid status");
        }
        return normalized;
    }

    private LocalDate requiredDate(LocalDate value, String message) {
        if (value == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "DATE", message);
        }
        return value;
    }

    private String required(String value, int min, int max, String message) {
        String text = blank(value);
        if (text == null || text.length() < min) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "TEXT", message);
        }
        if (text.length() > max) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "TEXT", "Use " + max + " characters or fewer");
        }
        return text;
    }

    private String optional(String value, int max) {
        String text = blank(value);
        if (text != null && text.length() > max) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "TEXT", "Use " + max + " characters or fewer");
        }
        return text;
    }

    private String blank(String value) {
        if (value == null) {
            return null;
        }
        String text = value.trim();
        return text.isEmpty() ? null : text;
    }

    private String label(String status) {
        return status.toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    private record Subject(UUID farmId, UUID animalId, UUID herdId, String label) {
    }

    private record PreparedDose(InventoryItem item, InventoryBatch batch, BigDecimal quantity, Integer withdrawalDays, StockIssueRequest request) {
        LocalDate endsOn(LocalDate administered) {
            return withdrawalDays == null ? null : administered.plusDays(withdrawalDays);
        }
    }

    private record Lookup(
            Map<UUID, Farm> farms,
            Map<UUID, Animal> animals,
            Map<UUID, HerdBatch> herds,
            Map<UUID, Species> species,
            Map<UUID, InventoryItem> items,
            Map<UUID, InventoryBatch> batches,
            Map<UUID, TreatmentDose> doses,
            Map<UUID, VaccinationProtocol> protocols
    ) {
    }
}
