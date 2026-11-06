package com.shambanexa.implementation;

import com.shambanexa.common.ApiException;
import com.shambanexa.dto.AdjustRequest;
import com.shambanexa.dto.AdjustmentDecisionRequest;
import com.shambanexa.dto.AlertAckBody;
import com.shambanexa.dto.InventoryAdjustmentResponse;
import com.shambanexa.dto.InventoryMovementResponse;
import com.shambanexa.dto.InventoryReportView;
import com.shambanexa.dto.InventoryRequest;
import com.shambanexa.dto.InventoryResponse;
import com.shambanexa.dto.StockAlertView;
import com.shambanexa.dto.StockBatchView;
import com.shambanexa.dto.StockIssueRequest;
import com.shambanexa.dto.StockReceiptRequest;
import com.shambanexa.dto.StockTransferRequest;
import com.shambanexa.dto.SupplierBody;
import com.shambanexa.dto.SupplierRecord;
import com.shambanexa.entity.Farm;
import com.shambanexa.entity.InventoryAdjustment;
import com.shambanexa.entity.InventoryAlertAck;
import com.shambanexa.entity.InventoryBatch;
import com.shambanexa.entity.InventoryItem;
import com.shambanexa.entity.InventoryLedgerEntry;
import com.shambanexa.entity.StockSupplier;
import com.shambanexa.entity.Tenant;
import com.shambanexa.repository.InventoryAdjustmentRepository;
import com.shambanexa.repository.InventoryAlertAckRepository;
import com.shambanexa.repository.InventoryItemRepository;
import com.shambanexa.repository.InventoryLedgerRepository;
import com.shambanexa.repository.InventoryReferenceRepository;
import com.shambanexa.repository.StockSupplierRepository;
import com.shambanexa.repository.TenantRepository;
import com.shambanexa.security.AccessGuard;
import com.shambanexa.service.AuditTrail;
import com.shambanexa.service.FarmService;
import com.shambanexa.service.InventoryService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class InventoryServiceImpl implements InventoryService {

    private final InventoryItemRepository inventory;
    private final InventoryLedgerRepository ledger;
    private final InventoryAdjustmentRepository adjustments;
    private final InventoryBatchBook book;
    private final StockSupplierRepository suppliers;
    private final InventoryReferenceRepository references;
    private final InventoryAlertAckRepository acks;
    private final TenantRepository tenants;
    private final FarmService farms;
    private final AccessGuard access;
    private final AuditTrail audit;

    public InventoryServiceImpl(InventoryItemRepository inventory, InventoryLedgerRepository ledger,
                                InventoryAdjustmentRepository adjustments, InventoryBatchBook book,
                                StockSupplierRepository suppliers, InventoryReferenceRepository references,
                                InventoryAlertAckRepository acks, TenantRepository tenants, FarmService farms,
                                AccessGuard access, AuditTrail audit) {
        this.inventory = inventory;
        this.ledger = ledger;
        this.adjustments = adjustments;
        this.book = book;
        this.suppliers = suppliers;
        this.references = references;
        this.acks = acks;
        this.tenants = tenants;
        this.farms = farms;
        this.access = access;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<InventoryResponse> inventory() {
        Map<UUID, Farm> farmMap = farmMap();
        return inventory.findByTenantIdOrderByNameAsc(access.tenantId()).stream()
                .filter(item -> access.canReadFarm(item.getFarmId()))
                .map(item -> toInventory(item, farmMap))
                .toList();
    }

    @Transactional
    public InventoryResponse createInventory(InventoryRequest request) {
        access.requireAuthority("INVENTORY_WRITE");
        access.checkFarm(request.farmId());
        String category = category(request.category());
        String lot = blank(request.lotCode());
        requireBatch(category, lot, request.expiresOn());
        String sku = request.sku().trim().toUpperCase();
        if (inventory.existsByTenantIdAndFarmIdAndSkuIgnoreCase(access.tenantId(), request.farmId(), sku)) {
            throw new ApiException(HttpStatus.CONFLICT, "DUPLICATE_SKU", "That SKU is already used on this farm");
        }
        InventoryItem item = new InventoryItem();
        item.setTenantId(access.tenantId());
        item.setFarmId(request.farmId());
        item.setSku(sku);
        item.setName(request.name().trim());
        item.setCategory(category);
        item.setQuantityOnHand(BigDecimal.ZERO);
        item.setReservedQuantity(BigDecimal.ZERO);
        item.setUnit(request.unit().trim());
        item.setReorderPoint(request.reorderPoint());
        item.setReorderQuantity(request.reorderQuantity());
        item.setUnitCost(request.unitCost());
        item.setSellingPrice(request.sellingPrice());
        item.setSupplierName(blank(request.supplierName()));
        item.setStoreName(blank(request.storeName()));
        item.setLotCode(lot);
        item.setExpiresOn(request.expiresOn());
        item.setDescription(blank(request.description()));
        item.setBrand(blank(request.brand()));
        item.setProductTypeId(request.productTypeId());
        item.setGenericName(blank(request.genericName()));
        item.setMedicineClassId(request.medicineClassId());
        item.setDosageForm(blank(request.dosageForm()));
        item.setStrength(blank(request.strength()));
        item.setSpeciesLabel(blank(request.speciesLabel()));
        item.setPrescriptionRequired(request.prescriptionRequired());
        item.setStorageNote(blank(request.storageNote()));
        item.setRegistrationNo(blank(request.registrationNo()));
        item.setWithdrawalDays(request.withdrawalDays());
        item.setSafetyNote(blank(request.safetyNote()));
        item.setFeedTypeId(request.feedTypeId());
        item.setGrowthStage(blank(request.growthStage()));
        item.setPackSize(blank(request.packSize()));
        item.setFormulation(blank(request.formulation()));
        item.setManufacturedOn(request.manufacturedOn());
        item.setBestBeforeOn(request.bestBeforeOn());
        item.setOpenDays(request.openDays());
        item.setPerishable("MEDICINE".equals(category) || "FEED".equals(category));
        linkSupplier(item);
        inventory.save(item);
        InventoryBatch batch = book.prepare(item, lot, request.manufacturedOn(), request.expiresOn(), request.bestBeforeOn(),
                book.today(), request.openDays(), item.getSupplierName(), item.getSupplierId(), item.getStoreName(), request.unitCost(), false);
        post(item, batch, "RECEIPT", request.quantityOnHand(), "Stock receipt", null, false);
        audit.record("INVENTORY_CREATED", "INVENTORY", item.getId(), item.getSku());
        return toInventory(item, farmMap());
    }

    @Transactional
    public InventoryResponse receive(UUID id, StockReceiptRequest request) {
        InventoryItem item = writable(id);
        String reason = request.reason().trim();
        if (blank(request.purchaseOrder()) != null) {
            reason = reason + " · PO " + request.purchaseOrder().trim();
        }
        if (request.lines() != null && !request.lines().isEmpty()) {
            for (StockReceiptRequest.BatchLine line : request.lines()) {
                receiveLine(item, line.quantity(), reason, line.lotCode(), line.expiresOn(), line.manufacturedOn(),
                        line.bestBeforeOn(), request.receivedOn(), request.openDays(), request.unitCost() == null ? line.unitCost() : request.unitCost(),
                        request.supplierName(), line.storeName() == null ? request.storeName() : line.storeName(), request.inspection(), request.allowExpired(), request.openedOn());
            }
            return toInventory(item, farmMap());
        }
        receiveLine(item, request.quantity(), reason, request.lotCode(), request.expiresOn(), request.manufacturedOn(),
                request.bestBeforeOn(), request.receivedOn(), request.openDays(), request.unitCost(), request.supplierName(),
                request.storeName(), request.inspection(), request.allowExpired(), request.openedOn());
        return toInventory(item, farmMap());
    }

    private void receiveLine(InventoryItem item, BigDecimal quantity, String reason, String lot, LocalDate expiry,
                             LocalDate manufactured, LocalDate bestBefore, LocalDate received, Integer openDays,
                             BigDecimal unitCost, String supplierName, String storeName, String inspection, Boolean allowExpired,
                             LocalDate openedOn) {
        LocalDate today = book.today();
        LocalDate when = received == null ? today : received;
        checkDates(manufactured, when, expiry, bestBefore, today);
        boolean expired = expiry != null && expiry.isBefore(today);
        boolean hold = "HOLD".equalsIgnoreCase(inspection);
        if ("REJECTED".equalsIgnoreCase(inspection)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INSPECTION_REJECTED", "A rejected delivery is not added to stock");
        }
        if (expired && !Boolean.TRUE.equals(allowExpired)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "EXPIRED_DELIVERY", "This delivery is already expired. A super admin can quarantine it instead of adding it to usable stock.");
        }
        if (expired && !access.canManageAccess()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "A super admin can accept an expired delivery into quarantine");
        }
        String lotCode = blank(lot);
        LocalDate batchExpiry = expiry == null ? item.getExpiresOn() : expiry;
        String batchLot = lotCode == null ? item.getLotCode() : lotCode;
        requireBatch(item.getCategory(), batchLot, item.getCategory().equals("MEDICINE") ? (batchExpiry == null ? item.getExpiresOn() : batchExpiry) : batchExpiry);
        if (lotCode != null) {
            item.setLotCode(lotCode);
        }
        if (expiry != null) {
            item.setExpiresOn(expiry);
        }
        if (bestBefore != null) {
            item.setBestBeforeOn(bestBefore);
        }
        if (manufactured != null) {
            item.setManufacturedOn(manufactured);
        }
        if (unitCost != null) {
            item.setUnitCost(unitCost);
        }
        if (blank(supplierName) != null) {
            item.setSupplierName(blank(supplierName));
            linkSupplier(item);
        }
        if (blank(storeName) != null) {
            item.setStoreName(blank(storeName));
        }
        boolean quarantine = expired || hold;
        InventoryBatch batch = book.prepare(item, batchLot, manufactured, item.getExpiresOn(), item.getBestBeforeOn(), when,
                openDays, item.getSupplierName(), item.getSupplierId(), item.getStoreName(), unitCost, quarantine);
        if (openedOn != null) {
            batch.setOpenedOn(openedOn);
        }
        post(item, batch, "RECEIPT", quantity, reason.trim(), null, true);
    }

    @Transactional
    public InventoryResponse issue(UUID id, StockIssueRequest request) {
        InventoryItem item = writable(id);
        LocalDate today = book.today();
        InventoryBatch recommended = book.fefo(item.getTenantId(), item.getId(), today);
        if (recommended == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "NO_USABLE_BATCH", "No open batch is eligible to issue. Expired, quarantined, and disposed stock stays on the record.");
        }
        InventoryBatch batch = request.batchId() == null ? recommended : book.require(item.getTenantId(), request.batchId());
        if (batch == null || !batch.getItemId().equals(item.getId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "BATCH_NOT_FOUND", "Choose a batch of this product");
        }
        if (!batch.getId().equals(recommended.getId()) && blank(request.overrideReason()) == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FEFO_OVERRIDE", "The earliest eligible batch is " + recommended.getLotCode() + ". Record a reason to issue a different batch.");
        }
        String reason = request.reason().trim();
        if (!batch.getId().equals(recommended.getId())) {
            reason = reason + " Override of " + recommended.getLotCode() + ": " + request.overrideReason().trim();
        }
        post(item, batch, "ISSUE", request.quantity().negate(), reason, null, false);
        return toInventory(item, farmMap());
    }

    @Transactional
    public InventoryResponse transfer(UUID id, StockTransferRequest request) {
        InventoryItem source = writable(id);
        if (source.getFarmId().equals(request.destinationFarmId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SAME_FARM", "Choose another farm. Each farm keeps one stock line per SKU.");
        }
        access.checkFarm(request.destinationFarmId());
        try {
            InventoryStock.apply(source.getQuantityOnHand(), source.getReservedQuantity(), request.quantity().negate());
        } catch (IllegalArgumentException ex) {
            throw negative();
        }
        LocalDate today = book.today();
        InventoryBatch recommended = book.fefo(source.getTenantId(), source.getId(), today);
        InventoryBatch sourceBatch = request.batchId() == null ? recommended : book.require(source.getTenantId(), request.batchId());
        if (sourceBatch == null || !sourceBatch.getItemId().equals(source.getId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "BATCH_NOT_FOUND", "Choose a batch of this product");
        }
        if (recommended != null && !sourceBatch.getId().equals(recommended.getId()) && blank(request.overrideReason()) == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FEFO_OVERRIDE", "The earliest eligible batch is " + recommended.getLotCode() + ". Record a reason to move a different batch.");
        }
        if (!InventoryExpiry.usable(sourceBatch, today)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "BATCH_BLOCKED", "Expired, quarantined, and disposed batches are not transferred as usable stock");
        }
        InventoryItem destination = inventory.findByTenantIdAndFarmIdAndSkuIgnoreCase(
                        access.tenantId(), request.destinationFarmId(), source.getSku())
                .orElseGet(() -> newDestination(source, request));
        if (!destination.getUnit().equals(source.getUnit())) {
            throw new ApiException(HttpStatus.CONFLICT, "UNIT_MISMATCH", "The destination stock line uses a different unit");
        }
        String store = blank(request.destinationStore());
        if (store != null) {
            destination.setStoreName(store);
        }
        destination.setLotCode(sourceBatch.getLotCode());
        destination.setExpiresOn(sourceBatch.getExpiresOn());
        destination.setBestBeforeOn(sourceBatch.getBestBeforeOn());
        UUID group = UUID.randomUUID();
        String reason = request.reason().trim();
        post(source, sourceBatch, "TRANSFER_OUT", request.quantity().negate(), reason, group, false);
        InventoryBatch destinationBatch = book.prepare(destination, sourceBatch.getLotCode(), sourceBatch.getManufacturedOn(),
                sourceBatch.getExpiresOn(), sourceBatch.getBestBeforeOn(), book.today(), sourceBatch.getOpenDays(),
                sourceBatch.getSupplierName(), sourceBatch.getSupplierId(), destination.getStoreName(), sourceBatch.getUnitCost(), false);
        post(destination, destinationBatch, "TRANSFER_IN", request.quantity(), reason, group, false);
        return toInventory(source, farmMap());
    }

    @Transactional
    public InventoryAdjustmentResponse adjust(UUID id, AdjustRequest request) {
        InventoryItem item = writable(id);
        boolean hold = holdReason(request.reasonCode());
        if (request.delta().signum() == 0 && !hold) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ZERO_ADJUSTMENT", "Enter a quantity change");
        }
        if (request.delta().signum() != 0) {
            try {
                InventoryStock.apply(item.getQuantityOnHand(), item.getReservedQuantity(), request.delta());
            } catch (IllegalArgumentException ex) {
                throw negative();
            }
        }
        if (request.batchId() != null) {
            InventoryBatch chosen = book.require(item.getTenantId(), request.batchId());
            if (chosen == null || !chosen.getItemId().equals(item.getId())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "BATCH_NOT_FOUND", "Choose a batch of this product");
            }
        }
        InventoryAdjustment row = new InventoryAdjustment();
        row.setTenantId(access.tenantId());
        row.setItemId(item.getId());
        row.setFarmId(item.getFarmId());
        row.setDelta(request.delta());
        row.setReason(request.reason().trim());
        row.setReasonCode(blank(request.reasonCode()));
        row.setNotes(blank(request.notes()));
        row.setBatchId(request.batchId());
        row.setQuantityBefore(item.getQuantityOnHand());
        row.setStatus("PENDING");
        row.setRequestedBy(access.current().id());
        row.setRequestedByName(access.current().fullName());
        row.setRequestedAt(Instant.now());
        adjustments.save(row);
        audit.record("INVENTORY_ADJUSTMENT_REQUESTED", "INVENTORY", item.getId(), request.reason().trim());
        audit.activity("INVENTORY", "Stock adjustment requested", item.getName() + " · " + request.reason().trim(), item.getFarmId());
        return toAdjustment(row, item, farmMap());
    }

    @Transactional
    public InventoryAdjustmentResponse decide(UUID id, AdjustmentDecisionRequest request) {
        if (!access.canManageAccess()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "A super admin approves stock adjustments");
        }
        InventoryAdjustment row = adjustments.findByIdAndTenantId(id, access.tenantId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ADJUSTMENT_NOT_FOUND", "Adjustment not found"));
        access.checkFarm(row.getFarmId());
        if (!"PENDING".equals(row.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "ALREADY_DECIDED", "This adjustment has already been decided");
        }
        InventoryItem item = inventory.findByIdAndTenantId(row.getItemId(), access.tenantId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ITEM_NOT_FOUND", "Inventory item not found"));
        row.setDecidedBy(access.current().id());
        row.setDecidedByName(access.current().fullName());
        row.setDecidedAt(Instant.now());
        if (!request.approve()) {
            row.setStatus("REJECTED");
            adjustments.save(row);
            audit.record("INVENTORY_ADJUSTMENT_REJECTED", "INVENTORY", item.getId(), row.getReason());
            audit.activity("INVENTORY", "Stock adjustment rejected", item.getName() + " · " + row.getReason(), item.getFarmId());
            return toAdjustment(row, item, farmMap());
        }
        InventoryBatch batch = resolveBatch(row, item);
        if (row.getDelta().signum() == 0) {
            if (batch != null) {
                book.mark(batch, "QUARANTINED");
                book.refreshDisplay(item, book.today());
                inventory.save(item);
            }
            row.setStatus("APPROVED");
            adjustments.save(row);
            audit.record("INVENTORY_ADJUSTMENT_APPROVED", "INVENTORY", item.getId(), row.getReason());
            return toAdjustment(row, item, farmMap());
        }
        InventoryLedgerEntry line = post(item, batch, "ADJUSTMENT", row.getDelta(), row.getReason(), null, true);
        if (batch != null && batch.getQuantityRemaining().signum() == 0 && disposalReason(row.getReasonCode())) {
            book.mark(batch, "DISPOSED");
            book.refreshDisplay(item, book.today());
            item.setQuantityOnHand(book.physical(item.getTenantId(), item.getId()));
            inventory.save(item);
        }
        row.setStatus("APPROVED");
        row.setLedgerId(line.getId());
        adjustments.save(row);
        audit.record("INVENTORY_ADJUSTMENT_APPROVED", "INVENTORY", item.getId(), row.getReason());
        return toAdjustment(row, item, farmMap());
    }

    @Transactional(readOnly = true)
    public List<InventoryMovementResponse> ledger() {
        Map<UUID, InventoryItem> items = itemsById();
        Map<UUID, Farm> farmMap = farmMap();
        return ledger.findByTenantIdOrderByOccurredAtDesc(access.tenantId()).stream()
                .filter(line -> access.canReadFarm(line.getFarmId()))
                .map(line -> toMovement(line, items.get(line.getItemId()), farmMap))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<InventoryAdjustmentResponse> adjustments() {
        Map<UUID, InventoryItem> items = itemsById();
        Map<UUID, Farm> farmMap = farmMap();
        return adjustments.findByTenantIdOrderByRequestedAtDesc(access.tenantId()).stream()
                .filter(row -> access.canReadFarm(row.getFarmId()))
                .map(row -> toAdjustment(row, items.get(row.getItemId()), farmMap))
                .toList();
    }

    private InventoryItem writable(UUID id) {
        access.requireAuthority("INVENTORY_WRITE");
        InventoryItem item = inventory.findByIdAndTenantId(id, access.tenantId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ITEM_NOT_FOUND", "Inventory item not found"));
        access.checkFarm(item.getFarmId());
        return item;
    }

    private InventoryItem newDestination(InventoryItem source, StockTransferRequest request) {
        InventoryItem created = new InventoryItem();
        created.setTenantId(access.tenantId());
        created.setFarmId(request.destinationFarmId());
        created.setSku(source.getSku());
        created.setName(source.getName());
        created.setCategory(source.getCategory());
        created.setQuantityOnHand(BigDecimal.ZERO);
        created.setReservedQuantity(BigDecimal.ZERO);
        created.setUnit(source.getUnit());
        created.setReorderPoint(source.getReorderPoint());
        created.setUnitCost(source.getUnitCost());
        created.setSupplierName(source.getSupplierName());
        created.setSupplierId(source.getSupplierId());
        created.setStoreName(blank(request.destinationStore()));
        created.setLotCode(source.getLotCode());
        created.setExpiresOn(source.getExpiresOn());
        created.setBestBeforeOn(source.getBestBeforeOn());
        created.setManufacturedOn(source.getManufacturedOn());
        created.setOpenDays(source.getOpenDays());
        created.setPerishable(source.isPerishable());
        created.setProductTypeId(source.getProductTypeId());
        created.setDescription(source.getDescription());
        created.setBrand(source.getBrand());
        created.setGenericName(source.getGenericName());
        created.setMedicineClassId(source.getMedicineClassId());
        created.setDosageForm(source.getDosageForm());
        created.setStrength(source.getStrength());
        created.setSpeciesLabel(source.getSpeciesLabel());
        created.setPrescriptionRequired(source.getPrescriptionRequired());
        created.setStorageNote(source.getStorageNote());
        created.setRegistrationNo(source.getRegistrationNo());
        created.setWithdrawalDays(source.getWithdrawalDays());
        created.setSafetyNote(source.getSafetyNote());
        created.setFeedTypeId(source.getFeedTypeId());
        created.setGrowthStage(source.getGrowthStage());
        created.setPackSize(source.getPackSize());
        created.setFormulation(source.getFormulation());
        return inventory.save(created);
    }

    private InventoryLedgerEntry post(InventoryItem item, InventoryBatch batch, String type, BigDecimal delta, String reason,
                                      UUID relatedId, boolean allowBlocked) {
        if (batch != null && delta.signum() != 0) {
            try {
                book.change(batch, delta, allowBlocked, book.today());
            } catch (IllegalArgumentException ex) {
                throw negative();
            } catch (IllegalStateException ex) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "BATCH_BLOCKED",
                        "This batch cannot leave usable stock. It is expired, quarantined, or disposed.");
            }
        }
        try {
            InventoryStock.apply(item.getQuantityOnHand(), item.getReservedQuantity(), delta);
        } catch (IllegalArgumentException ex) {
            throw negative();
        }
        BigDecimal physical = batch == null
                ? item.getQuantityOnHand().add(delta)
                : book.physical(item.getTenantId(), item.getId());
        item.setQuantityOnHand(physical);
        if (batch != null) {
            book.refreshDisplay(item, book.today());
        }
        inventory.save(item);
        InventoryLedgerEntry line = new InventoryLedgerEntry();
        line.setTenantId(access.tenantId());
        line.setItemId(item.getId());
        line.setFarmId(item.getFarmId());
        line.setMovementType(type);
        line.setQuantityDelta(delta);
        line.setQuantityAfter(physical);
        line.setUnit(item.getUnit());
        line.setReason(reason);
        line.setLotCode(batch == null ? item.getLotCode() : batch.getLotCode());
        line.setExpiresOn(batch == null ? item.getExpiresOn() : batch.getExpiresOn());
        line.setSupplierName(item.getSupplierName());
        line.setStoreName(item.getStoreName());
        line.setRelatedId(relatedId);
        line.setBatchId(batch == null ? null : batch.getId());
        line.setActorId(access.current().id());
        line.setActorName(access.current().fullName());
        line.setOccurredAt(Instant.now());
        audit.activity("INVENTORY", title(type), item.getName() + " · " + plain(delta) + " " + item.getUnit() + " · " + reason, item.getFarmId());
        return ledger.save(line);
    }

    private InventoryResponse toInventory(InventoryItem item, Map<UUID, Farm> farmMap) {
        List<InventoryBatch> rows = book.of(item.getTenantId(), item.getId());
        LocalDate today = book.today();
        int window = book.windowDays(item.getTenantId());
        BigDecimal usable = book.usable(rows, today);
        BigDecimal available = usable.subtract(item.getReservedQuantity());
        if (available.signum() < 0) {
            available = BigDecimal.ZERO;
        }
        InventoryBatch shown = InventoryExpiry.earliest(rows, today);
        if (shown == null) {
            shown = rows.stream().filter(batch -> !"DISPOSED".equals(batch.getStatus())).findFirst().orElse(null);
        }
        Integer days = shown == null ? null : InventoryExpiry.daysUntil(InventoryExpiry.useBy(shown), today);
        String expiryStatus = shown == null ? "VALID" : InventoryExpiry.status(shown, today, window);
        int batchCount = (int) rows.stream()
                .filter(batch -> !"DISPOSED".equals(batch.getStatus()) && batch.getQuantityRemaining().signum() > 0)
                .count();
        return new InventoryResponse(
                item.getId(),
                item.getSku(),
                item.getName(),
                item.getCategory(),
                item.getFarmId(),
                farmName(farmMap, item.getFarmId()),
                item.getQuantityOnHand(),
                item.getReservedQuantity(),
                available,
                item.getUnit(),
                item.getReorderPoint(),
                available.compareTo(item.getReorderPoint()) <= 0,
                item.getLotCode(),
                item.getExpiresOn(),
                item.getUnitCost(),
                InventoryStock.value(item.getQuantityOnHand(), item.getUnitCost()),
                item.getSupplierName(),
                item.getStoreName(),
                item.getVersion() == null ? 0 : item.getVersion(),
                usable,
                batchCount,
                days,
                expiryStatus,
                typeName(item.getProductTypeId()),
                item.getWithdrawalDays()
        );
    }

    private InventoryMovementResponse toMovement(InventoryLedgerEntry line, InventoryItem item, Map<UUID, Farm> farmMap) {
        return new InventoryMovementResponse(
                line.getId(),
                line.getItemId(),
                item == null ? "" : item.getName(),
                item == null ? "" : item.getSku(),
                line.getFarmId(),
                farmName(farmMap, line.getFarmId()),
                line.getMovementType(),
                line.getQuantityDelta(),
                line.getQuantityAfter(),
                line.getUnit(),
                line.getReason(),
                line.getLotCode(),
                line.getExpiresOn(),
                line.getSupplierName(),
                line.getStoreName(),
                line.getActorName(),
                line.getOccurredAt()
        );
    }

    private InventoryAdjustmentResponse toAdjustment(InventoryAdjustment row, InventoryItem item, Map<UUID, Farm> farmMap) {
        return new InventoryAdjustmentResponse(
                row.getId(),
                row.getItemId(),
                item == null ? "" : item.getName(),
                item == null ? "" : item.getSku(),
                row.getFarmId(),
                farmName(farmMap, row.getFarmId()),
                row.getDelta(),
                item == null ? "" : item.getUnit(),
                row.getReason(),
                row.getStatus(),
                row.getRequestedByName(),
                row.getRequestedAt(),
                row.getDecidedByName(),
                row.getDecidedAt()
        );
    }

    private Map<UUID, InventoryItem> itemsById() {
        return inventory.findByTenantIdOrderByNameAsc(access.tenantId()).stream()
                .collect(Collectors.toMap(InventoryItem::getId, Function.identity()));
    }

    private Map<UUID, Farm> farmMap() {
        return farms.visibleFarms().stream().collect(Collectors.toMap(Farm::getId, Function.identity()));
    }

    private String farmName(Map<UUID, Farm> farmMap, UUID farmId) {
        Farm farm = farmMap.get(farmId);
        return farm == null ? "" : farm.getName();
    }

    private String category(String raw) {
        try {
            return InventoryStock.category(raw);
        } catch (IllegalArgumentException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CATEGORY_INVALID", "Use feed, medicine, equipment, or supplies");
        }
    }

    private void requireBatch(String category, String lot, LocalDate expiry) {
        try {
            InventoryStock.requireMedicineBatch(category, lot, expiry);
        } catch (IllegalArgumentException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "BATCH_REQUIRED", "Medicines need a batch code and an expiry date");
        }
    }

    private ApiException negative() {
        return new ApiException(HttpStatus.BAD_REQUEST, "NEGATIVE_STOCK", "Available stock cannot go negative");
    }

    private String title(String type) {
        return switch (type) {
            case "RECEIPT" -> "Stock receipt";
            case "ISSUE" -> "Stock issue";
            case "TRANSFER_OUT" -> "Transfer out";
            case "TRANSFER_IN" -> "Transfer in";
            case "ADJUSTMENT" -> "Stock adjustment";
            default -> "Stock movement";
        };
    }

    private String plain(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    private String blank(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private void checkDates(LocalDate manufactured, LocalDate received, LocalDate expiry, LocalDate bestBefore, LocalDate today) {
        if (manufactured != null && manufactured.isAfter(today)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "DATE_INVALID", "The manufacturing date cannot be in the future");
        }
        if (received.isAfter(today)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "DATE_INVALID", "The receiving date cannot be in the future");
        }
        if (manufactured != null && received.isBefore(manufactured)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "DATE_INVALID", "The receiving date cannot be before the manufacturing date");
        }
        if (manufactured != null && expiry != null && !expiry.isAfter(manufactured)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "DATE_INVALID", "The expiry date must be after the manufacturing date");
        }
        if (manufactured != null && bestBefore != null && bestBefore.isBefore(manufactured)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "DATE_INVALID", "The best-before date cannot be before the manufacturing date");
        }
    }

    private void linkSupplier(InventoryItem item) {
        String name = item.getSupplierName();
        if (name == null || name.isBlank()) {
            return;
        }
        StockSupplier supplier = suppliers.findByTenantIdAndNameIgnoreCase(item.getTenantId(), name.trim())
                .orElseGet(() -> {
                    StockSupplier created = new StockSupplier();
                    created.setTenantId(item.getTenantId());
                    created.setName(name.trim());
                    created.setSupplierType(supplierType(item.getCategory()));
                    created.setStatus("ACTIVE");
                    Instant now = Instant.now();
                    created.setCreatedAt(now);
                    created.setUpdatedAt(now);
                    return suppliers.save(created);
                });
        item.setSupplierId(supplier.getId());
    }

    private String supplierType(String category) {
        if ("MEDICINE".equals(category)) {
            return "MEDICAL";
        }
        if ("FEED".equals(category)) {
            return "FEED";
        }
        if ("SUPPLY".equals(category)) {
            return "AGRICULTURAL";
        }
        return "GENERAL";
    }

    private boolean holdReason(String code) {
        if (code == null) {
            return false;
        }
        String value = code.trim().toUpperCase(java.util.Locale.ROOT);
        return value.equals("QUARANTINE") || value.equals("RECALL") || value.equals("CONTAMINATED");
    }

    private boolean disposalReason(String code) {
        if (code == null) {
            return false;
        }
        String value = code.trim().toUpperCase(java.util.Locale.ROOT);
        return value.equals("DISPOSAL") || value.equals("EXPIRED") || value.equals("SPOILED") || value.equals("RECALL");
    }

    private InventoryBatch resolveBatch(InventoryAdjustment row, InventoryItem item) {
        if (row.getBatchId() != null) {
            InventoryBatch chosen = book.require(item.getTenantId(), row.getBatchId());
            if (chosen == null || !chosen.getItemId().equals(item.getId())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "BATCH_NOT_FOUND", "Choose a batch of this product");
            }
            return chosen;
        }
        return book.of(item.getTenantId(), item.getId()).stream()
                .filter(batch -> !"DISPOSED".equals(batch.getStatus()))
                .findFirst()
                .orElse(null);
    }

    private String typeName(UUID typeId) {
        if (typeId == null) {
            return null;
        }
        return references.findByIdAndTenantId(typeId, access.tenantId()).map(row -> row.getName()).orElse(null);
    }

    @Transactional(readOnly = true)
    public List<StockBatchView> batches() {
        Map<UUID, InventoryItem> items = itemsById();
        Map<UUID, Farm> farmMap = farmMap();
        LocalDate today = book.today();
        int window = book.windowDays(access.tenantId());
        return book.all(access.tenantId()).stream()
                .filter(batch -> access.canReadFarm(batch.getFarmId()))
                .map(batch -> toBatch(batch, items.get(batch.getItemId()), farmMap, today, window))
                .toList();
    }

    @Transactional(readOnly = true)
    public StockBatchView fefo(UUID itemId) {
        InventoryItem item = inventory.findByIdAndTenantId(itemId, access.tenantId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ITEM_NOT_FOUND", "Inventory item not found"));
        access.checkFarm(item.getFarmId());
        InventoryBatch batch = book.fefo(item.getTenantId(), item.getId(), book.today());
        if (batch == null) {
            return null;
        }
        return toBatch(batch, item, farmMap(), book.today(), book.windowDays(item.getTenantId()));
    }

    @Transactional(readOnly = true)
    public List<StockAlertView> alerts() {
        Map<UUID, InventoryItem> items = itemsById();
        Map<UUID, Farm> farmMap = farmMap();
        LocalDate today = book.today();
        int window = book.windowDays(access.tenantId());
        Map<String, InventoryAlertAck> ackMap = acks.findByTenantId(access.tenantId()).stream()
                .collect(Collectors.toMap(row -> row.getBatchId() + ":" + row.getAlertKind(), Function.identity(), (left, right) -> left));
        List<StockAlertView> rows = new ArrayList<>();
        for (InventoryItem item : items.values()) {
            if (!access.canReadFarm(item.getFarmId())) {
                continue;
            }
            InventoryResponse view = toInventory(item, farmMap);
            if (view.available().signum() <= 0) {
                rows.add(alert("OUT", "High", item, view, "Reorder or receive stock", ackMap));
            } else if (view.lowStock()) {
                rows.add(alert("LOW", "Medium", item, view, "Reorder before the store runs out", ackMap));
            }
        }
        for (InventoryBatch batch : book.all(access.tenantId())) {
            if (!access.canReadFarm(batch.getFarmId()) || "DISPOSED".equals(batch.getStatus())) {
                continue;
            }
            InventoryItem item = items.get(batch.getItemId());
            if (item == null || batch.getQuantityRemaining().signum() <= 0) {
                continue;
            }
            String state = InventoryExpiry.status(batch, today, window);
            String kind = switch (state) {
                case "EXPIRED" -> "EXPIRED";
                case "EXPIRING" -> "EXPIRING";
                case "BEST_BEFORE" -> "BEST_BEFORE";
                case "OPEN_USE" -> "OPEN_USE";
                case "QUARANTINED" -> "QUARANTINED";
                default -> null;
            };
            if (kind == null) {
                continue;
            }
            Integer days = InventoryExpiry.daysUntil(InventoryExpiry.useBy(batch), today);
            String severity = "EXPIRED".equals(kind) || "QUARANTINED".equals(kind) || (days != null && days <= 7) ? "High" : "Medium";
            String action = switch (kind) {
                case "EXPIRED" -> "Quarantine or dispose this batch";
                case "EXPIRING" -> "Issue this batch first";
                case "BEST_BEFORE" -> "Use this batch while quality is still good";
                case "OPEN_USE" -> "Finish the opened pack before the use-by date";
                default -> "Keep this batch out of usable stock";
            };
            InventoryAlertAck ack = ackMap.get(batch.getId() + ":" + kind);
            rows.add(new StockAlertView(batch.getId(), item.getId(), kind, severity, item.getName(), item.getCategory(),
                    batch.getLotCode(), batch.getQuantityRemaining(), InventoryExpiry.useBy(batch), days, action,
                    ack == null ? null : ack.getAssigneeName(), ack != null && ack.getAcknowledgedAt() != null,
                    farmName(farmMap, item.getFarmId())));
        }
        return rows;
    }

    @Transactional
    public StockAlertView acknowledge(AlertAckBody body) {
        access.requireAuthority("INVENTORY_WRITE");
        InventoryBatch batch = book.require(access.tenantId(), body.batchId());
        if (batch == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "BATCH_NOT_FOUND", "Batch not found");
        }
        access.checkFarm(batch.getFarmId());
        String kind = body.alertKind().trim().toUpperCase(java.util.Locale.ROOT);
        InventoryAlertAck ack = acks.findByTenantIdAndBatchIdAndAlertKind(access.tenantId(), batch.getId(), kind)
                .orElseGet(InventoryAlertAck::new);
        if (ack.getId() == null) {
            ack.setTenantId(access.tenantId());
            ack.setBatchId(batch.getId());
            ack.setAlertKind(kind);
        }
        ack.setAssigneeName(blank(body.assigneeName()));
        ack.setNote(blank(body.note()));
        ack.setAcknowledgedAt(Instant.now());
        ack.setAcknowledgedBy(access.current().id());
        ack.setAcknowledgedByName(access.current().fullName());
        acks.save(ack);
        return alerts().stream()
                .filter(row -> batch.getId().equals(row.batchId()) && kind.equals(row.kind()))
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "ALERT_CLOSED", "That alert is no longer open"));
    }

    @Transactional(readOnly = true)
    public List<SupplierRecord> supplierRecords() {
        Map<UUID, InventoryItem> items = itemsById();
        return suppliers.findByTenantIdOrderByNameAsc(access.tenantId()).stream()
                .map(row -> new SupplierRecord(row.getId(), row.getName(), row.getSupplierType(), row.getPhone(), row.getLocation(),
                        row.getRegistrationNo(), row.getLicenseNo(), row.getLeadDays(), row.getStatus(),
                        (int) items.values().stream().filter(item -> row.getId().equals(item.getSupplierId()) && access.canReadFarm(item.getFarmId())).count()))
                .toList();
    }

    @Transactional
    public SupplierRecord saveSupplier(SupplierBody body) {
        access.requireAuthority("INVENTORY_WRITE");
        String type = body.supplierType() == null ? "GENERAL" : body.supplierType().trim().toUpperCase(java.util.Locale.ROOT);
        if (!type.equals("MEDICAL") && !type.equals("FEED") && !type.equals("AGRICULTURAL") && !type.equals("GENERAL")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SUPPLIER_TYPE", "Use Medical, Feed, Agricultural, or General");
        }
        StockSupplier row = body.id() == null ? new StockSupplier() : suppliers.findByIdAndTenantId(body.id(), access.tenantId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SUPPLIER_NOT_FOUND", "Supplier not found"));
        Instant now = Instant.now();
        if (row.getId() == null) {
            row.setTenantId(access.tenantId());
            row.setCreatedAt(now);
            row.setStatus("ACTIVE");
        }
        row.setName(body.name().trim());
        row.setSupplierType(type);
        row.setPhone(blank(body.phone()));
        row.setLocation(blank(body.location()));
        row.setRegistrationNo(blank(body.registrationNo()));
        row.setLicenseNo(blank(body.licenseNo()));
        row.setLeadDays(body.leadDays());
        if (blank(body.status()) != null) {
            row.setStatus(body.status().trim().toUpperCase(java.util.Locale.ROOT));
        }
        row.setUpdatedAt(now);
        suppliers.save(row);
        return supplierRecords().stream().filter(record -> record.id().equals(row.getId())).findFirst().orElseThrow();
    }

    @Transactional(readOnly = true)
    public InventoryReportView report() {
        List<InventoryResponse> rows = inventory();
        List<StockBatchView> batchRows = batches();
        BigDecimal total = rows.stream().map(InventoryResponse::stockValue).filter(java.util.Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        List<InventoryReportView.CategoryValue> byCategory = rows.stream()
                .collect(Collectors.groupingBy(InventoryResponse::category))
                .entrySet().stream()
                .map(entry -> new InventoryReportView.CategoryValue(entry.getKey(), entry.getValue().size(),
                        entry.getValue().stream().map(InventoryResponse::stockValue).filter(java.util.Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add)))
                .sorted(Comparator.comparing(InventoryReportView.CategoryValue::category))
                .toList();
        int[] windows = {7, 14, 30, 60, 90};
        List<InventoryReportView.WindowCount> expiring = new ArrayList<>();
        for (int days : windows) {
            long count = batchRows.stream().filter(batch -> batch.daysLeft() != null && batch.daysLeft() >= 0 && batch.daysLeft() <= days && !"DISPOSED".equals(batch.status()) && !"QUARANTINED".equals(batch.status())).count();
            expiring.add(new InventoryReportView.WindowCount(days, count));
        }
        BigDecimal expiredValue = BigDecimal.ZERO;
        long expired = 0;
        long near = 0;
        long quarantined = 0;
        for (StockBatchView batch : batchRows) {
            if ("EXPIRED".equals(batch.status())) {
                expired++;
                if (batch.unitCost() != null) {
                    expiredValue = expiredValue.add(batch.unitCost().multiply(batch.quantityRemaining()));
                }
            }
            if ("EXPIRING".equals(batch.status()) || "BEST_BEFORE".equals(batch.status()) || "OPEN_USE".equals(batch.status())) {
                near++;
            }
            if ("QUARANTINED".equals(batch.status())) {
                quarantined++;
            }
        }
        String currency = tenants.findById(access.tenantId()).map(Tenant::getCurrencyCode).orElse("USD");
        return new InventoryReportView(total, currency, byCategory, expiring, expiredValue, expired, near, quarantined);
    }

    private StockAlertView alert(String kind, String severity, InventoryItem item, InventoryResponse view, String action,
                                 Map<String, InventoryAlertAck> ackMap) {
        InventoryAlertAck ack = view.lotCode() == null ? null : ackMap.values().stream()
                .filter(row -> kind.equals(row.getAlertKind()) && row.getBatchId() != null)
                .findFirst().orElse(null);
        UUID batchId = book.of(item.getTenantId(), item.getId()).stream()
                .filter(batch -> !"DISPOSED".equals(batch.getStatus()))
                .map(InventoryBatch::getId)
                .findFirst()
                .orElse(null);
        if (batchId != null) {
            ack = ackMap.get(batchId + ":" + kind);
        }
        return new StockAlertView(batchId, item.getId(), kind, severity, item.getName(), item.getCategory(), view.lotCode(),
                view.available(), view.expiresOn(), view.daysLeft(), action, ack == null ? null : ack.getAssigneeName(),
                ack != null && ack.getAcknowledgedAt() != null, view.farmName());
    }

    private StockBatchView toBatch(InventoryBatch batch, InventoryItem item, Map<UUID, Farm> farmMap, LocalDate today, int window) {
        return new StockBatchView(
                batch.getId(),
                batch.getItemId(),
                item == null ? "" : item.getName(),
                item == null ? "" : item.getSku(),
                item == null ? "" : item.getCategory(),
                item == null ? null : typeName(item.getProductTypeId()),
                batch.getFarmId(),
                farmName(farmMap, batch.getFarmId()),
                batch.getLotCode(),
                batch.getQuantityReceived(),
                batch.getQuantityRemaining(),
                batch.getManufacturedOn(),
                batch.getReceivedOn(),
                batch.getExpiresOn(),
                batch.getBestBeforeOn(),
                batch.getOpenedOn(),
                batch.getOpenDays(),
                InventoryExpiry.useBy(batch),
                InventoryExpiry.daysUntil(InventoryExpiry.useBy(batch), today),
                InventoryExpiry.status(batch, today, window),
                batch.getStoreName(),
                batch.getSupplierName(),
                batch.getUnitCost(),
                item == null ? "" : item.getUnit()
        );
    }
}
