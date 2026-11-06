package com.shambanexa.implementation;

import com.shambanexa.common.ApiException;
import com.shambanexa.dto.MasterDtos.BreedBody;
import com.shambanexa.dto.MasterDtos.CategoryView;
import com.shambanexa.dto.MasterDtos.DetailView;
import com.shambanexa.dto.MasterDtos.HistoryLine;
import com.shambanexa.dto.MasterDtos.ImportPreview;
import com.shambanexa.dto.MasterDtos.ImportRow;
import com.shambanexa.dto.MasterDtos.PageView;
import com.shambanexa.dto.MasterDtos.RecordView;
import com.shambanexa.dto.MasterDtos.ReferenceBody;
import com.shambanexa.dto.MasterDtos.SpeciesBody;
import com.shambanexa.entity.AuditEvent;
import com.shambanexa.entity.Breed;
import com.shambanexa.entity.InventoryAdjustment;
import com.shambanexa.entity.InventoryBatch;
import com.shambanexa.entity.InventoryItem;
import com.shambanexa.entity.InventoryReference;
import com.shambanexa.entity.Species;
import com.shambanexa.entity.UserAccount;
import com.shambanexa.repository.AnimalRepository;
import com.shambanexa.repository.AuditEventRepository;
import com.shambanexa.repository.BreedRepository;
import com.shambanexa.repository.FishCycleRepository;
import com.shambanexa.repository.HerdBatchRepository;
import com.shambanexa.repository.InventoryAdjustmentRepository;
import com.shambanexa.repository.InventoryBatchRepository;
import com.shambanexa.repository.InventoryItemRepository;
import com.shambanexa.repository.InventoryReferenceRepository;
import com.shambanexa.repository.SpeciesRepository;
import com.shambanexa.repository.UserAccountRepository;
import com.shambanexa.security.AccessGuard;
import com.shambanexa.service.AuditTrail;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class MasterDataService {

    static final String SPECIES = "SPECIES";
    static final String BREEDS = "BREEDS";
    private static final List<String> INVENTORY_KINDS = List.of(
            "INV_CATEGORY", "INV_TYPE", "INV_MED_CLASS", "INV_FEED_TYPE", "INV_UNIT",
            "INV_STORAGE", "INV_REASON", "INV_WINDOW", "INV_STATUS");

    private final AccessGuard access;
    private final AuditTrail audit;
    private final SpeciesRepository species;
    private final BreedRepository breeds;
    private final AnimalRepository animals;
    private final HerdBatchRepository batches;
    private final FishCycleRepository fish;
    private final AuditEventRepository audits;
    private final UserAccountRepository users;
    private final InventoryReferenceRepository references;
    private final InventoryItemRepository inventory;
    private final InventoryBatchRepository stockBatches;
    private final InventoryAdjustmentRepository adjustments;

    public MasterDataService(AccessGuard access, AuditTrail audit, SpeciesRepository species, BreedRepository breeds,
                             AnimalRepository animals, HerdBatchRepository batches, FishCycleRepository fish,
                             AuditEventRepository audits, UserAccountRepository users, InventoryReferenceRepository references,
                             InventoryItemRepository inventory, InventoryBatchRepository stockBatches,
                             InventoryAdjustmentRepository adjustments) {
        this.access = access;
        this.audit = audit;
        this.species = species;
        this.breeds = breeds;
        this.animals = animals;
        this.batches = batches;
        this.fish = fish;
        this.audits = audits;
        this.users = users;
        this.references = references;
        this.inventory = inventory;
        this.stockBatches = stockBatches;
        this.adjustments = adjustments;
    }

    @Transactional(readOnly = true)
    public List<CategoryView> categories() {
        UUID tenant = access.tenantId();
        List<Species> kinds = species.findByTenantIdOrderBySortOrderAsc(tenant);
        List<Breed> breedRows = breeds.findByTenantIdOrderByNameAsc(tenant);
        List<CategoryView> rows = new ArrayList<>();
        rows.add(new CategoryView(SPECIES, "Species", "Animals and herds are filed under a species.", kinds.size(), active(kinds.stream().map(Species::getStatus).toList())));
        rows.add(new CategoryView(BREEDS, "Breeds", "A breed belongs to one species.", breedRows.size(), active(breedRows.stream().map(Breed::getStatus).toList())));
        List<InventoryReference> stored = references.findByTenantIdOrderBySortOrderAscNameAsc(tenant);
        rows.add(inventoryCategory("INV_CATEGORY", "Inventory categories", "Feed, medicine, supplies, and equipment.", stored));
        rows.add(inventoryCategory("INV_TYPE", "Product types", "Types within an inventory category.", stored));
        rows.add(inventoryCategory("INV_MED_CLASS", "Medicine classes", "Antibiotic, vaccine, dewormer, and other classes.", stored));
        rows.add(inventoryCategory("INV_FEED_TYPE", "Feed types", "Concentrate, forage, and supplements.", stored));
        rows.add(inventoryCategory("INV_UNIT", "Units of measurement", "kg, litre, bottle, dose, and other units.", stored));
        rows.add(inventoryCategory("INV_STORAGE", "Storage locations", "Stores, fridges, and barns.", stored));
        rows.add(inventoryCategory("INV_REASON", "Adjustment reasons", "Expiry, damage, spoilage, and disposal.", stored));
        rows.add(inventoryCategory("INV_WINDOW", "Expiry warning windows", "How many days before expiry counts as expiring soon.", stored));
        rows.add(inventoryCategory("INV_STATUS", "Stock statuses", "Valid, expiring, expired, and other batch states.", stored));
        return rows;
    }

    @Transactional(readOnly = true)
    public PageView page(String category, String q, String status, UUID parentId, String sort, String direction, int page, int size) {
        String kind = category(category);
        List<RecordView> rows = SPECIES.equals(kind) ? speciesRows() : BREEDS.equals(kind) ? breedRows(parentId) : referenceRows(kind, parentId);
        String query = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        String wanted = blank(status);
        List<RecordView> filtered = rows.stream()
                .filter(row -> wanted == null || wanted.equals(row.status()))
                .filter(row -> query.isEmpty() || (row.code() + " " + row.name() + " " + (row.groupLabel() == null ? "" : row.groupLabel())).toLowerCase(Locale.ROOT).contains(query))
                .sorted(order(sort, direction, kind))
                .toList();
        int limit = Math.min(50, Math.max(1, size));
        int startPage = Math.max(0, page);
        int from = Math.min(filtered.size(), startPage * limit);
        int to = Math.min(filtered.size(), from + limit);
        return new PageView(kind, label(kind), filtered.size(), startPage, limit, access.canManageAccess(), filtered.subList(from, to));
    }

    @Transactional(readOnly = true)
    public DetailView detail(String category, UUID id) {
        RecordView row = find(category(category), id);
        List<HistoryLine> history = audits.findTop20ByTenantIdAndEntityTypeAndEntityIdOrderByRecordedAtDesc(access.tenantId(), category(category), id)
                .stream()
                .map(event -> new HistoryLine(event.getAction(), event.getDetail() == null ? "" : event.getDetail(), event.getRecordedAt(), actor(event)))
                .toList();
        return new DetailView(row, history);
    }

    @Transactional
    public RecordView createSpecies(SpeciesBody body) {
        editor();
        String code = code(body.code());
        String name = name(body.name(), 80);
        rejectSpeciesClash(null, code, name);
        Species row = new Species();
        row.setTenantId(access.tenantId());
        row.setCode(code);
        row.setName(name);
        row.setCategory(categoryName(body.category()));
        row.setChartColor(color(body.chartColor()));
        row.setSortOrder(body.sortOrder());
        row.setIndividualTracking(body.individualTracking());
        row.setStatus("ACTIVE");
        row.setOrigin("ADMIN");
        stampNew(row);
        species.save(row);
        audit.record("SPECIES_CREATED", SPECIES, row.getId(), row.getName());
        audit.activity("SETTINGS", "Species added", row.getName() + " · " + row.getCode(), null);
        return speciesView(row, names());
    }

    @Transactional
    public RecordView updateSpecies(UUID id, SpeciesBody body) {
        editor();
        Species row = loadSpecies(id);
        String code = code(body.code());
        String name = name(body.name(), 80);
        if (!code.equals(row.getCode())) {
            if (codeLocked(row.getOrigin(), speciesUsage(row.getId()) > 0)) {
                throw new ApiException(HttpStatus.CONFLICT, "CODE_LOCKED", "The code stays " + row.getCode() + " because livestock records use it");
            }
        }
        rejectSpeciesClash(row.getId(), code, name);
        row.setCode(code);
        row.setName(name);
        row.setCategory(categoryName(body.category()));
        row.setChartColor(color(body.chartColor()));
        row.setSortOrder(body.sortOrder());
        row.setIndividualTracking(body.individualTracking());
        stamp(row);
        species.save(row);
        audit.record("SPECIES_UPDATED", SPECIES, row.getId(), row.getName());
        audit.activity("SETTINGS", "Species updated", row.getName() + " · " + row.getCode(), null);
        return speciesView(row, names());
    }

    @Transactional
    public RecordView statusSpecies(UUID id, boolean active) {
        editor();
        Species row = loadSpecies(id);
        row.setStatus(active ? "ACTIVE" : "INACTIVE");
        stamp(row);
        species.save(row);
        String title = active ? "Species activated" : "Species deactivated";
        audit.record(active ? "SPECIES_ACTIVATED" : "SPECIES_DEACTIVATED", SPECIES, row.getId(), row.getName());
        audit.activity("SETTINGS", title, row.getName(), null);
        return speciesView(row, names());
    }

    @Transactional
    public void deleteSpecies(UUID id) {
        editor();
        Species row = loadSpecies(id);
        long usage = speciesUsage(id);
        if ("SYSTEM".equals(row.getOrigin()) || usage > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "IN_USE", row.getName() + " is still used by livestock records, so it stays in the list. Deactivate it instead.");
        }
        species.delete(row);
        audit.record("SPECIES_REMOVED", SPECIES, id, row.getName());
        audit.activity("SETTINGS", "Species removed", row.getName(), null);
    }

    @Transactional
    public RecordView createBreed(BreedBody body) {
        editor();
        Species parent = loadSpecies(body.speciesId());
        String code = code(body.code());
        String name = name(body.name(), 120);
        rejectBreedClash(parent.getId(), null, code, name);
        Breed row = new Breed();
        row.setTenantId(access.tenantId());
        row.setSpeciesId(parent.getId());
        row.setCode(code);
        row.setName(name);
        row.setStatus("ACTIVE");
        row.setOrigin("ADMIN");
        stampNew(row);
        breeds.save(row);
        audit.record("BREED_CREATED", BREEDS, row.getId(), parent.getName() + " · " + row.getName());
        audit.activity("SETTINGS", "Breed added", parent.getName() + " · " + row.getName(), null);
        return breedView(row, parent, names());
    }

    @Transactional
    public RecordView updateBreed(UUID id, BreedBody body) {
        editor();
        Breed row = loadBreed(id);
        Species parent = loadSpecies(body.speciesId());
        String code = code(body.code());
        String name = name(body.name(), 120);
        if (!code.equals(row.getCode()) && codeLocked(row.getOrigin(), breedUsage(row.getId()) > 0)) {
            throw new ApiException(HttpStatus.CONFLICT, "CODE_LOCKED", "The code stays " + row.getCode() + " because livestock records use it");
        }
        if (!parent.getId().equals(row.getSpeciesId()) && breedUsage(row.getId()) > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "SPECIES_LOCKED", "Animals already use this breed, so its species stays the same");
        }
        rejectBreedClash(parent.getId(), row.getId(), code, name);
        row.setSpeciesId(parent.getId());
        row.setCode(code);
        row.setName(name);
        stamp(row);
        breeds.save(row);
        audit.record("BREED_UPDATED", BREEDS, row.getId(), parent.getName() + " · " + row.getName());
        audit.activity("SETTINGS", "Breed updated", parent.getName() + " · " + row.getName(), null);
        return breedView(row, parent, names());
    }

    @Transactional
    public RecordView statusBreed(UUID id, boolean active) {
        editor();
        Breed row = loadBreed(id);
        row.setStatus(active ? "ACTIVE" : "INACTIVE");
        stamp(row);
        breeds.save(row);
        Species parent = loadSpecies(row.getSpeciesId());
        String title = active ? "Breed activated" : "Breed deactivated";
        audit.record(active ? "BREED_ACTIVATED" : "BREED_DEACTIVATED", BREEDS, row.getId(), row.getName());
        audit.activity("SETTINGS", title, parent.getName() + " · " + row.getName(), null);
        return breedView(row, parent, names());
    }

    @Transactional
    public void deleteBreed(UUID id) {
        editor();
        Breed row = loadBreed(id);
        if ("SYSTEM".equals(row.getOrigin()) || breedUsage(id) > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "IN_USE", row.getName() + " is still used by livestock records, so it stays in the list. Deactivate it instead.");
        }
        breeds.delete(row);
        audit.record("BREED_REMOVED", BREEDS, id, row.getName());
        audit.activity("SETTINGS", "Breed removed", row.getName(), null);
    }

    @Transactional(readOnly = true)
    public byte[] export(String category, String format) {
        String kind = category(category);
        List<String> headers = SPECIES.equals(kind)
                ? List.of("code", "name", "category", "chartColor", "sortOrder", "individualTracking", "status", "origin")
                : BREEDS.equals(kind)
                ? List.of("species", "code", "name", "status", "origin")
                : List.of("code", "name", "detail", "numericValue", "sortOrder", "status", "origin");
        List<List<String>> rows = new ArrayList<>();
        if (SPECIES.equals(kind)) {
            for (Species row : species.findByTenantIdOrderBySortOrderAsc(access.tenantId())) {
                rows.add(List.of(row.getCode(), row.getName(), row.getCategory(), row.getChartColor(),
                        String.valueOf(row.getSortOrder()), String.valueOf(row.isIndividualTracking()), row.getStatus(), row.getOrigin()));
            }
        } else if (BREEDS.equals(kind)) {
            Map<UUID, Species> parents = speciesMap();
            for (Breed row : breeds.findByTenantIdOrderByNameAsc(access.tenantId())) {
                Species parent = parents.get(row.getSpeciesId());
                rows.add(List.of(parent == null ? "" : parent.getCode(), row.getCode(), row.getName(), row.getStatus(), row.getOrigin()));
            }
        } else {
            for (InventoryReference row : references.findByTenantIdAndKindOrderBySortOrderAscNameAsc(access.tenantId(), kind)) {
                rows.add(List.of(row.getCode(), row.getName(), row.getDetail() == null ? "" : row.getDetail(),
                        row.getNumericValue() == null ? "" : String.valueOf(row.getNumericValue()),
                        String.valueOf(row.getSortOrder()), row.getStatus(), row.getOrigin()));
            }
        }
        return "xlsx".equalsIgnoreCase(format) ? MasterWorkbook.xlsx(headers, rows) : MasterWorkbook.csv(headers, rows);
    }

    @Transactional
    public ImportPreview importSpecies(boolean apply, boolean updateExisting, String filename, byte[] bytes) {
        editor();
        List<Map<String, String>> table = table(filename, bytes, List.of("code", "name"));
        List<ImportRow> outcome = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        int created = 0;
        int updated = 0;
        int skipped = 0;
        int failed = 0;
        int line = 1;
        for (Map<String, String> raw : table) {
            line++;
            String codeValue = raw.getOrDefault("code", "");
            String nameValue = raw.getOrDefault("name", "");
            try {
                String code = code(codeValue);
                if (!seen.add(code)) {
                    throw new ApiException(HttpStatus.CONFLICT, "DUPLICATE_CODE", "This code is repeated in the file");
                }
                String name = name(nameValue, 80);
                Species existing = species.findByTenantIdAndCodeIgnoreCase(access.tenantId(), code).orElse(null);
                if (existing == null) {
                    if (apply) {
                        createSpecies(new SpeciesBody(code, name, defaulted(raw.get("category"), "LIVESTOCK"),
                                defaulted(raw.get("chartcolor"), "#1E8330"), integer(raw.get("sortorder"), nextSort()), tracking(raw.get("individualtracking"), true)));
                    }
                    created++;
                    outcome.add(new ImportRow(line, code, name, "CREATE", "Will be added"));
                } else if (!updateExisting) {
                    skipped++;
                    outcome.add(new ImportRow(line, existing.getCode(), existing.getName(), "SKIP", "Already stored"));
                } else {
                    if (apply) {
                        updateSpecies(existing.getId(), new SpeciesBody(existing.getCode(), name, defaulted(raw.get("category"), existing.getCategory()),
                                defaulted(raw.get("chartcolor"), existing.getChartColor()), integer(raw.get("sortorder"), existing.getSortOrder()),
                                tracking(raw.get("individualtracking"), existing.isIndividualTracking())));
                        if (raw.get("status") != null && !raw.get("status").isBlank()) {
                            statusSpecies(existing.getId(), activeFlag(raw.get("status")));
                        }
                    }
                    updated++;
                    outcome.add(new ImportRow(line, existing.getCode(), name, "UPDATE", "Will update the stored species"));
                }
            } catch (ApiException ex) {
                failed++;
                outcome.add(new ImportRow(line, codeValue, nameValue, "FAIL", ex.getMessage()));
            }
        }
        return finish(outcome, created, updated, skipped, failed, apply);
    }

    @Transactional
    public ImportPreview importBreeds(boolean apply, boolean updateExisting, String filename, byte[] bytes) {
        editor();
        List<Map<String, String>> table = table(filename, bytes, List.of("species", "code", "name"));
        List<ImportRow> outcome = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        int created = 0;
        int updated = 0;
        int skipped = 0;
        int failed = 0;
        int line = 1;
        for (Map<String, String> raw : table) {
            line++;
            String codeValue = raw.getOrDefault("code", "");
            String nameValue = raw.getOrDefault("name", "");
            try {
                Species parent = species.findByTenantIdAndCodeIgnoreCase(access.tenantId(), code(raw.getOrDefault("species", "")))
                        .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "SPECIES_UNKNOWN", "No species uses that code"));
                String code = code(codeValue);
                if (!seen.add(parent.getCode() + ":" + code)) {
                    throw new ApiException(HttpStatus.CONFLICT, "DUPLICATE_CODE", "This code is repeated in the file");
                }
                String name = name(nameValue, 120);
                Breed existing = breeds.findByTenantIdAndSpeciesIdAndCodeIgnoreCase(access.tenantId(), parent.getId(), code).orElse(null);
                if (existing == null) {
                    if (apply) {
                        createBreed(new BreedBody(parent.getId(), code, name));
                    }
                    created++;
                    outcome.add(new ImportRow(line, code, name, "CREATE", "Will be added under " + parent.getName()));
                } else if (!updateExisting) {
                    skipped++;
                    outcome.add(new ImportRow(line, existing.getCode(), existing.getName(), "SKIP", "Already stored"));
                } else {
                    if (apply) {
                        updateBreed(existing.getId(), new BreedBody(parent.getId(), existing.getCode(), name));
                        if (raw.get("status") != null && !raw.get("status").isBlank()) {
                            statusBreed(existing.getId(), activeFlag(raw.get("status")));
                        }
                    }
                    updated++;
                    outcome.add(new ImportRow(line, existing.getCode(), name, "UPDATE", "Will update the stored breed"));
                }
            } catch (ApiException ex) {
                failed++;
                outcome.add(new ImportRow(line, codeValue, nameValue, "FAIL", ex.getMessage()));
            }
        }
        return finish(outcome, created, updated, skipped, failed, apply);
    }

    private ImportPreview finish(List<ImportRow> rows, int created, int updated, int skipped, int failed, boolean apply) {
        if (!apply) {
            return new ImportPreview(created, updated, skipped, failed, rows);
        }
        List<ImportRow> saved = rows.stream().map(row -> switch (row.action()) {
            case "CREATE" -> new ImportRow(row.line(), row.code(), row.name(), row.action(), "Added");
            case "UPDATE" -> new ImportRow(row.line(), row.code(), row.name(), row.action(), "Updated");
            default -> row;
        }).toList();
        return new ImportPreview(created, updated, skipped, failed, saved);
    }

    private List<Map<String, String>> table(String filename, byte[] bytes, List<String> required) {
        List<List<String>> grid = MasterWorkbook.read(filename, bytes);
        if (grid.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "EMPTY_FILE", "The file has no header row");
        }
        if (grid.size() > 501) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "TOO_MANY_ROWS", "Import up to 500 records at a time");
        }
        List<String> headers = grid.get(0).stream().map(this::header).toList();
        for (String key : required) {
            if (!headers.contains(key)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "HEADER_MISSING", "The file needs a " + key + " column");
            }
        }
        List<Map<String, String>> rows = new ArrayList<>();
        for (int i = 1; i < grid.size(); i++) {
            List<String> cells = grid.get(i);
            if (cells.stream().allMatch(cell -> cell == null || cell.isBlank())) {
                continue;
            }
            Map<String, String> row = new HashMap<>();
            for (int c = 0; c < headers.size() && c < cells.size(); c++) {
                row.put(headers.get(c), cells.get(c) == null ? "" : cells.get(c).trim());
            }
            rows.add(row);
        }
        if (rows.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "EMPTY_FILE", "The file has no records under the header");
        }
        return rows;
    }

    private RecordView find(String kind, UUID id) {
        Map<UUID, String> people = names();
        if (SPECIES.equals(kind)) {
            return speciesView(loadSpecies(id), people);
        }
        if (kind.startsWith("INV_")) {
            InventoryReference row = loadReference(id);
            if (!kind.equals(row.getKind())) {
                throw new ApiException(HttpStatus.NOT_FOUND, "RECORD_NOT_FOUND", "That record is not in this list");
            }
            return referenceView(row, people);
        }
        Breed row = loadBreed(id);
        return breedView(row, loadSpecies(row.getSpeciesId()), people);
    }

    private List<RecordView> speciesRows() {
        Map<UUID, String> people = names();
        return species.findByTenantIdOrderBySortOrderAsc(access.tenantId()).stream()
                .map(row -> speciesView(row, people))
                .toList();
    }

    private List<RecordView> breedRows(UUID parentId) {
        Map<UUID, String> people = names();
        Map<UUID, Species> parents = speciesMap();
        return breeds.findByTenantIdOrderByNameAsc(access.tenantId()).stream()
                .filter(row -> parentId == null || parentId.equals(row.getSpeciesId()))
                .map(row -> breedView(row, parents.get(row.getSpeciesId()), people))
                .toList();
    }

    private RecordView speciesView(Species row, Map<UUID, String> people) {
        long animals = this.animals.countByTenantIdAndSpeciesId(access.tenantId(), row.getId());
        long herds = batches.countByTenantIdAndSpeciesId(access.tenantId(), row.getId());
        long cycles = fish.countByTenantIdAndSpeciesId(access.tenantId(), row.getId());
        long breedCount = breeds.countByTenantIdAndSpeciesId(access.tenantId(), row.getId());
        long usage = animals + herds + cycles;
        return new RecordView(row.getId(), row.getCode(), row.getName(), row.getStatus(), row.getOrigin(), row.getCategory(),
                null, row.getChartColor(), row.getSortOrder(), row.isIndividualTracking(), usage, usageLabel(breedCount, herds, animals, cycles),
                codeLocked(row.getOrigin(), usage > 0), row.getCreatedAt(), row.getUpdatedAt(),
                person(people, row.getCreatedBy(), row.getOrigin()), person(people, row.getUpdatedBy(), row.getOrigin()), null, null);
    }

    private RecordView breedView(Breed row, Species parent, Map<UUID, String> people) {
        long animals = this.animals.countByTenantIdAndBreedId(access.tenantId(), row.getId());
        long herds = batches.countByTenantIdAndBreedId(access.tenantId(), row.getId());
        long usage = animals + herds;
        return new RecordView(row.getId(), row.getCode(), row.getName(), row.getStatus(), row.getOrigin(),
                parent == null ? "Species missing" : parent.getName(), row.getSpeciesId(), null, null, null, usage,
                usageLabel(0, herds, animals, 0), codeLocked(row.getOrigin(), usage > 0), row.getCreatedAt(), row.getUpdatedAt(),
                person(people, row.getCreatedBy(), row.getOrigin()), person(people, row.getUpdatedBy(), row.getOrigin()), null, null);
    }

    private String usageLabel(long breedCount, long herds, long animals, long cycles) {
        List<String> parts = new ArrayList<>();
        if (breedCount > 0) {
            parts.add(breedCount + (breedCount == 1 ? " breed" : " breeds"));
        }
        if (herds > 0) {
            parts.add(herds + (herds == 1 ? " herd" : " herds"));
        }
        if (animals > 0) {
            parts.add(animals + (animals == 1 ? " animal" : " animals"));
        }
        if (cycles > 0) {
            parts.add(cycles + (cycles == 1 ? " fish cycle" : " fish cycles"));
        }
        return parts.isEmpty() ? "Not linked to livestock" : String.join(", ", parts);
    }

    private void rejectSpeciesClash(UUID self, String code, String name) {
        species.findByTenantIdAndCodeIgnoreCase(access.tenantId(), code)
                .filter(row -> !row.getId().equals(self))
                .ifPresent(row -> {
                    throw new ApiException(HttpStatus.CONFLICT, "DUPLICATE_CODE", "A species already uses the code " + row.getCode());
                });
        species.findByTenantIdAndNameIgnoreCase(access.tenantId(), name)
                .filter(row -> !row.getId().equals(self))
                .ifPresent(row -> {
                    throw new ApiException(HttpStatus.CONFLICT, "DUPLICATE_NAME", "A species already uses the name " + row.getName());
                });
    }

    private void rejectBreedClash(UUID speciesId, UUID self, String code, String name) {
        breeds.findByTenantIdAndSpeciesIdAndCodeIgnoreCase(access.tenantId(), speciesId, code)
                .filter(row -> !row.getId().equals(self))
                .ifPresent(row -> {
                    throw new ApiException(HttpStatus.CONFLICT, "DUPLICATE_CODE", "That species already has a breed with the code " + row.getCode());
                });
        breeds.findByTenantIdAndSpeciesIdAndNameIgnoreCase(access.tenantId(), speciesId, name)
                .filter(row -> !row.getId().equals(self))
                .ifPresent(row -> {
                    throw new ApiException(HttpStatus.CONFLICT, "DUPLICATE_NAME", "That species already has a breed named " + row.getName());
                });
    }

    private long speciesUsage(UUID id) {
        return animals.countByTenantIdAndSpeciesId(access.tenantId(), id)
                + batches.countByTenantIdAndSpeciesId(access.tenantId(), id)
                + fish.countByTenantIdAndSpeciesId(access.tenantId(), id);
    }

    private long breedUsage(UUID id) {
        return animals.countByTenantIdAndBreedId(access.tenantId(), id) + batches.countByTenantIdAndBreedId(access.tenantId(), id);
    }

    private Species loadSpecies(UUID id) {
        return species.findByIdAndTenantId(id, access.tenantId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SPECIES_NOT_FOUND", "Species not found"));
    }

    private Breed loadBreed(UUID id) {
        return breeds.findByIdAndTenantId(id, access.tenantId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "BREED_NOT_FOUND", "Breed not found"));
    }

    private Map<UUID, Species> speciesMap() {
        Map<UUID, Species> map = new HashMap<>();
        for (Species row : species.findByTenantIdOrderBySortOrderAsc(access.tenantId())) {
            map.put(row.getId(), row);
        }
        return map;
    }

    private Map<UUID, String> names() {
        Map<UUID, String> map = new HashMap<>();
        for (UserAccount user : users.findByTenantIdOrderByFullNameAsc(access.tenantId())) {
            map.put(user.getId(), user.getFullName());
        }
        return map;
    }

    private String person(Map<UUID, String> people, UUID id, String origin) {
        if (id == null) {
            return "SYSTEM".equals(origin) ? "System" : "Not recorded";
        }
        return people.getOrDefault(id, "Former user");
    }

    private String actor(AuditEvent event) {
        if (event.getActorId() == null) {
            return "System";
        }
        return users.findById(event.getActorId()).map(UserAccount::getFullName).orElse("Former user");
    }

    private void stampNew(Species row) {
        Instant now = Instant.now();
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
        row.setCreatedBy(access.current().id());
        row.setUpdatedBy(access.current().id());
    }

    private void stampNew(Breed row) {
        Instant now = Instant.now();
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
        row.setCreatedBy(access.current().id());
        row.setUpdatedBy(access.current().id());
    }

    private void stamp(Species row) {
        row.setUpdatedAt(Instant.now());
        row.setUpdatedBy(access.current().id());
    }

    private void stamp(Breed row) {
        row.setUpdatedAt(Instant.now());
        row.setUpdatedBy(access.current().id());
    }

    private void editor() {
        if (!access.canManageAccess()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Only a super admin can change master data");
        }
    }

    private Comparator<RecordView> order(String sort, String direction, String kind) {
        Comparator<RecordView> comparator = switch (sort == null ? "" : sort) {
            case "code" -> Comparator.comparing(RecordView::code, String.CASE_INSENSITIVE_ORDER);
            case "name" -> Comparator.comparing(RecordView::name, String.CASE_INSENSITIVE_ORDER);
            case "status" -> Comparator.comparing(RecordView::status);
            case "updated" -> Comparator.comparing(RecordView::updatedAt, Comparator.nullsLast(Comparator.naturalOrder()));
            case "group" -> Comparator.comparing(RecordView::groupLabel, String.CASE_INSENSITIVE_ORDER);
            default -> SPECIES.equals(kind) || kind.startsWith("INV_")
                    ? Comparator.comparing((RecordView row) -> row.sortOrder() == null ? 0 : row.sortOrder()).thenComparing(RecordView::name, String.CASE_INSENSITIVE_ORDER)
                    : Comparator.comparing(RecordView::name, String.CASE_INSENSITIVE_ORDER);
        };
        return "desc".equalsIgnoreCase(direction) ? comparator.reversed() : comparator;
    }

    private String category(String value) {
        if (value != null && BREEDS.equalsIgnoreCase(value)) {
            return BREEDS;
        }
        if (value != null && SPECIES.equalsIgnoreCase(value)) {
            return SPECIES;
        }
        if (value != null && INVENTORY_KINDS.contains(value.toUpperCase(Locale.ROOT))) {
            return value.toUpperCase(Locale.ROOT);
        }
        throw new ApiException(HttpStatus.NOT_FOUND, "CATEGORY_UNKNOWN", "That master data list is not stored");
    }

    private String label(String kind) {
        return switch (kind) {
            case SPECIES -> "Species";
            case BREEDS -> "Breeds";
            case "INV_CATEGORY" -> "Inventory categories";
            case "INV_TYPE" -> "Product types";
            case "INV_MED_CLASS" -> "Medicine classes";
            case "INV_FEED_TYPE" -> "Feed types";
            case "INV_UNIT" -> "Units";
            case "INV_STORAGE" -> "Storage locations";
            case "INV_REASON" -> "Adjustment reasons";
            case "INV_WINDOW" -> "Expiry windows";
            case "INV_STATUS" -> "Stock statuses";
            default -> kind;
        };
    }

    private boolean codeLocked(String origin, boolean used) {
        return "SYSTEM".equals(origin) || used;
    }

    private String code(String value) {
        String cleaned = value == null ? "" : value.trim().toUpperCase(Locale.ROOT).replace(' ', '-');
        cleaned = cleaned.replaceAll("[^A-Z0-9-]", "");
        cleaned = cleaned.replaceAll("-{2,}", "-");
        cleaned = cleaned.replaceAll("^-|-$", "");
        if (cleaned.isBlank() || cleaned.length() > 40) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CODE_INVALID", "Enter a code of letters and numbers, up to 40 characters");
        }
        return cleaned;
    }

    private String name(String value, int max) {
        String cleaned = value == null ? "" : value.trim().replaceAll("\\s+", " ");
        if (cleaned.isBlank() || cleaned.length() > max) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "NAME_INVALID", "Enter a name up to " + max + " characters");
        }
        return cleaned;
    }

    private String categoryName(String value) {
        String cleaned = value == null ? "" : value.trim().toUpperCase(Locale.ROOT).replaceAll("\\s+", " ");
        if (!cleaned.matches("[A-Z][A-Z0-9 ]{0,39}")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CATEGORY_INVALID", "Use a category such as LIVESTOCK or AQUACULTURE");
        }
        return cleaned;
    }

    private String color(String value) {
        String cleaned = value == null ? "" : value.trim();
        if (!cleaned.matches("#[0-9A-Fa-f]{6}")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "COLOR_INVALID", "Use a color like #1E8330");
        }
        return cleaned.toUpperCase(Locale.ROOT);
    }

    private String header(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replace("_", "").replace(" ", "");
    }

    private String defaulted(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private int integer(String value, int fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SORT_INVALID", "Sort order must be a whole number");
        }
    }

    private boolean tracking(String value, boolean fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "true", "yes", "1" -> true;
            case "false", "no", "0" -> false;
            default -> throw new ApiException(HttpStatus.BAD_REQUEST, "TRACKING_INVALID", "Individual tracking must be yes or no");
        };
    }

    private boolean activeFlag(String value) {
        return switch (value.trim().toUpperCase(Locale.ROOT)) {
            case "ACTIVE", "YES", "TRUE" -> true;
            case "INACTIVE", "NO", "FALSE" -> false;
            default -> throw new ApiException(HttpStatus.BAD_REQUEST, "STATUS_INVALID", "Status must be ACTIVE or INACTIVE");
        };
    }

    private int nextSort() {
        return species.findByTenantIdOrderBySortOrderAsc(access.tenantId()).stream()
                .map(Species::getSortOrder)
                .max(Integer::compareTo)
                .orElse(0) + 1;
    }

    private long active(List<String> statuses) {
        return statuses.stream().filter("ACTIVE"::equals).count();
    }

    private String blank(String value) {
        if (value == null || value.isBlank() || "ALL".equalsIgnoreCase(value)) {
            return null;
        }
        String cleaned = value.trim().toUpperCase(Locale.ROOT);
        if (!"ACTIVE".equals(cleaned) && !"INACTIVE".equals(cleaned)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "STATUS_INVALID", "Filter by active or inactive");
        }
        return cleaned;
    }

    private CategoryView inventoryCategory(String kind, String label, String description, List<InventoryReference> stored) {
        List<InventoryReference> rows = stored.stream().filter(row -> kind.equals(row.getKind())).toList();
        return new CategoryView(kind, label, description, rows.size(), rows.stream().filter(row -> "ACTIVE".equals(row.getStatus())).count());
    }

    private List<RecordView> referenceRows(String kind, UUID parentId) {
        Map<UUID, String> people = names();
        UUID tenant = access.tenantId();
        List<InventoryItem> items = inventory.findByTenantIdOrderByNameAsc(tenant);
        List<InventoryBatch> batches = stockBatches.findByTenantIdOrderByExpiresOnAsc(tenant);
        List<InventoryAdjustment> requests = adjustments.findByTenantIdOrderByRequestedAtDesc(tenant);
        Map<UUID, String> parentNames = references.findByTenantIdOrderBySortOrderAscNameAsc(tenant).stream()
                .collect(java.util.stream.Collectors.toMap(InventoryReference::getId, InventoryReference::getName));
        return references.findByTenantIdAndKindOrderBySortOrderAscNameAsc(tenant, kind).stream()
                .filter(row -> parentId == null || parentId.equals(row.getParentId()))
                .map(row -> referenceView(row, people, referenceUsage(row, items, batches, requests), parentNames.get(row.getParentId())))
                .toList();
    }

    private RecordView referenceView(InventoryReference row, Map<UUID, String> people) {
        return referenceView(row, people, 0, null);
    }

    private RecordView referenceView(InventoryReference row, Map<UUID, String> people, long usage, String parentName) {
        String group = parentName == null ? (row.getDetail() == null ? "" : row.getDetail()) : parentName;
        String used = row.getNumericValue() == null
                ? (usage == 0 ? "Not used by stock" : usage + " in use")
                : row.getNumericValue() + (row.getNumericValue() == 1 ? " day" : " days");
        return new RecordView(row.getId(), row.getCode(), row.getName(), row.getStatus(), row.getOrigin(), group,
                row.getParentId(), null, row.getSortOrder(), null, usage, used, codeLocked(row.getOrigin(), usage > 0),
                row.getCreatedAt(), row.getUpdatedAt(), person(people, row.getCreatedBy(), row.getOrigin()),
                person(people, row.getUpdatedBy(), row.getOrigin()), row.getDetail(), row.getNumericValue());
    }

    private long referenceUsage(InventoryReference row, List<InventoryItem> items, List<InventoryBatch> batches, List<InventoryAdjustment> requests) {
        return switch (row.getKind()) {
            case "INV_CATEGORY" -> items.stream().filter(item -> row.getCode().equals(item.getCategory())).count()
                    + references.countByTenantIdAndParentId(row.getTenantId(), row.getId());
            case "INV_TYPE" -> items.stream().filter(item -> row.getId().equals(item.getProductTypeId())).count();
            case "INV_MED_CLASS" -> items.stream().filter(item -> row.getId().equals(item.getMedicineClassId())).count();
            case "INV_FEED_TYPE" -> items.stream().filter(item -> row.getId().equals(item.getFeedTypeId())).count();
            case "INV_UNIT" -> items.stream().filter(item -> row.getCode().equalsIgnoreCase(item.getUnit())).count();
            case "INV_STORAGE" -> batches.stream().filter(batch -> row.getName().equalsIgnoreCase(batch.getStoreName())).count();
            case "INV_REASON" -> requests.stream().filter(request -> row.getCode().equalsIgnoreCase(request.getReasonCode())).count();
            default -> 0;
        };
    }

    @Transactional
    public RecordView createReference(String kind, ReferenceBody body) {
        editor();
        String list = category(kind);
        InventoryReference row = new InventoryReference();
        row.setTenantId(access.tenantId());
        row.setKind(list);
        row.setOrigin("ADMIN");
        row.setStatus("ACTIVE");
        Instant now = Instant.now();
        row.setCreatedAt(now);
        row.setCreatedBy(access.current().id());
        fillReference(row, body, false);
        references.save(row);
        audit.record("REFERENCE_CREATED", list, row.getId(), row.getName());
        return referenceView(row, names());
    }

    @Transactional
    public RecordView updateReference(UUID id, ReferenceBody body) {
        editor();
        InventoryReference row = loadReference(id);
        fillReference(row, body, codeLocked(row.getOrigin(), referenceUsage(row) > 0));
        references.save(row);
        audit.record("REFERENCE_UPDATED", row.getKind(), row.getId(), row.getName());
        return referenceView(row, names());
    }

    @Transactional
    public RecordView statusReference(UUID id, boolean active) {
        editor();
        InventoryReference row = loadReference(id);
        row.setStatus(active ? "ACTIVE" : "INACTIVE");
        row.setUpdatedAt(Instant.now());
        row.setUpdatedBy(access.current().id());
        references.save(row);
        audit.record(active ? "REFERENCE_ACTIVATED" : "REFERENCE_DEACTIVATED", row.getKind(), row.getId(), row.getName());
        return referenceView(row, names());
    }

    @Transactional
    public void deleteReference(UUID id) {
        editor();
        InventoryReference row = loadReference(id);
        if ("SYSTEM".equals(row.getOrigin()) || referenceUsage(row) > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "IN_USE", row.getName() + " is a system record or it is still used, so it stays in the list. Deactivate it instead.");
        }
        references.delete(row);
        audit.record("REFERENCE_REMOVED", row.getKind(), id, row.getName());
    }

    private void fillReference(InventoryReference row, ReferenceBody body, boolean lockCode) {
        String nextCode = code(body.code());
        if (!lockCode) {
            rejectReferenceClash(row.getKind(), row.getId(), nextCode);
            row.setCode(nextCode);
        }
        row.setName(name(body.name(), 160));
        row.setParentId(checkedParent(row.getKind(), body.parentId()));
        row.setDetail(trim(body.detail(), 400));
        if ("INV_WINDOW".equals(row.getKind()) && (body.numericValue() == null || body.numericValue() < 0)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "WINDOW_INVALID", "Enter the number of days for this warning");
        }
        row.setNumericValue(body.numericValue());
        row.setSortOrder(body.sortOrder() == null ? 0 : body.sortOrder());
        row.setUpdatedAt(Instant.now());
        row.setUpdatedBy(access.current().id());
    }

    private UUID checkedParent(String kind, UUID parentId) {
        if ("INV_TYPE".equals(kind) && parentId == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PARENT_REQUIRED", "Choose the inventory category for this product type");
        }
        if (parentId == null) {
            return null;
        }
        InventoryReference parent = loadReference(parentId);
        if ("INV_TYPE".equals(kind) && !"INV_CATEGORY".equals(parent.getKind())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PARENT_INVALID", "A product type belongs to an inventory category");
        }
        return parent.getId();
    }

    private void rejectReferenceClash(String kind, UUID self, String code) {
        references.findByTenantIdAndKindAndCodeIgnoreCase(access.tenantId(), kind, code)
                .filter(row -> self == null || !row.getId().equals(self))
                .ifPresent(row -> {
                    throw new ApiException(HttpStatus.CONFLICT, "DUPLICATE_CODE", "This list already uses the code " + row.getCode());
                });
    }

    private long referenceUsage(InventoryReference row) {
        UUID tenant = access.tenantId();
        return referenceUsage(row, inventory.findByTenantIdOrderByNameAsc(tenant),
                stockBatches.findByTenantIdOrderByExpiresOnAsc(tenant),
                adjustments.findByTenantIdOrderByRequestedAtDesc(tenant));
    }

    private InventoryReference loadReference(UUID id) {
        return references.findByIdAndTenantId(id, access.tenantId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "RECORD_NOT_FOUND", "That record was not found"));
    }

    private String trim(String value, int max) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String cleaned = value.trim();
        if (cleaned.length() > max) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "DETAIL_INVALID", "Keep that note to " + max + " characters");
        }
        return cleaned;
    }
}
