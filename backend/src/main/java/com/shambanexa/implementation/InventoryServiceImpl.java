package com.shambanexa.implementation;

import com.shambanexa.common.ApiException;
import com.shambanexa.dto.AdjustRequest;
import com.shambanexa.dto.AdjustmentDecisionRequest;
import com.shambanexa.dto.InventoryAdjustmentResponse;
import com.shambanexa.dto.InventoryMovementResponse;
import com.shambanexa.dto.InventoryRequest;
import com.shambanexa.dto.InventoryResponse;
import com.shambanexa.dto.StockIssueRequest;
import com.shambanexa.dto.StockReceiptRequest;
import com.shambanexa.dto.StockTransferRequest;
import com.shambanexa.entity.Farm;
import com.shambanexa.entity.InventoryAdjustment;
import com.shambanexa.entity.InventoryItem;
import com.shambanexa.entity.InventoryLedgerEntry;
import com.shambanexa.repository.InventoryAdjustmentRepository;
import com.shambanexa.repository.InventoryItemRepository;
import com.shambanexa.repository.InventoryLedgerRepository;
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
    private final FarmService farms;
    private final AccessGuard access;
    private final AuditTrail audit;

    public InventoryServiceImpl(InventoryItemRepository inventory, InventoryLedgerRepository ledger,
                                InventoryAdjustmentRepository adjustments, FarmService farms,
                                AccessGuard access, AuditTrail audit) {
        this.inventory = inventory;
        this.ledger = ledger;
        this.adjustments = adjustments;
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
        item.setUnitCost(request.unitCost());
        item.setSupplierName(blank(request.supplierName()));
        item.setStoreName(blank(request.storeName()));
        item.setLotCode(lot);
        item.setExpiresOn(request.expiresOn());
        inventory.save(item);
        post(item, "RECEIPT", request.quantityOnHand(), "Stock receipt", lot, request.expiresOn(), null);
        audit.record("INVENTORY_CREATED", "INVENTORY", item.getId(), item.getSku());
        return toInventory(item, farmMap());
    }

    @Transactional
    public InventoryResponse receive(UUID id, StockReceiptRequest request) {
        InventoryItem item = writable(id);
        String lot = blank(request.lotCode());
        LocalDate expiry = request.expiresOn() == null ? item.getExpiresOn() : request.expiresOn();
        String batch = lot == null ? item.getLotCode() : lot;
        requireBatch(item.getCategory(), batch, expiry);
        if (lot != null) {
            item.setLotCode(lot);
        }
        if (request.expiresOn() != null) {
            item.setExpiresOn(request.expiresOn());
        }
        if (request.unitCost() != null) {
            item.setUnitCost(request.unitCost());
        }
        if (blank(request.supplierName()) != null) {
            item.setSupplierName(blank(request.supplierName()));
        }
        if (blank(request.storeName()) != null) {
            item.setStoreName(blank(request.storeName()));
        }
        post(item, "RECEIPT", request.quantity(), request.reason().trim(), item.getLotCode(), item.getExpiresOn(), null);
        return toInventory(item, farmMap());
    }

    @Transactional
    public InventoryResponse issue(UUID id, StockIssueRequest request) {
        InventoryItem item = writable(id);
        post(item, "ISSUE", request.quantity().negate(), request.reason().trim(), item.getLotCode(), item.getExpiresOn(), null);
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
        destination.setLotCode(source.getLotCode());
        destination.setExpiresOn(source.getExpiresOn());
        UUID group = UUID.randomUUID();
        String reason = request.reason().trim();
        post(source, "TRANSFER_OUT", request.quantity().negate(), reason, source.getLotCode(), source.getExpiresOn(), group);
        post(destination, "TRANSFER_IN", request.quantity(), reason, destination.getLotCode(), destination.getExpiresOn(), group);
        return toInventory(source, farmMap());
    }

    @Transactional
    public InventoryAdjustmentResponse adjust(UUID id, AdjustRequest request) {
        InventoryItem item = writable(id);
        if (request.delta().compareTo(BigDecimal.ZERO) == 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ZERO_ADJUSTMENT", "Enter a quantity change");
        }
        try {
            InventoryStock.apply(item.getQuantityOnHand(), item.getReservedQuantity(), request.delta());
        } catch (IllegalArgumentException ex) {
            throw negative();
        }
        InventoryAdjustment row = new InventoryAdjustment();
        row.setTenantId(access.tenantId());
        row.setItemId(item.getId());
        row.setFarmId(item.getFarmId());
        row.setDelta(request.delta());
        row.setReason(request.reason().trim());
        row.setStatus("PENDING");
        row.setRequestedBy(access.current().id());
        row.setRequestedByName(access.current().fullName());
        row.setRequestedAt(Instant.now());
        adjustments.save(row);
        audit.record("INVENTORY_ADJUSTMENT_REQUESTED", "INVENTORY", item.getId(), request.reason().trim());
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
            return toAdjustment(row, item, farmMap());
        }
        InventoryLedgerEntry line = post(item, "ADJUSTMENT", row.getDelta(), row.getReason(), item.getLotCode(), item.getExpiresOn(), null);
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
        created.setStoreName(blank(request.destinationStore()));
        created.setLotCode(source.getLotCode());
        created.setExpiresOn(source.getExpiresOn());
        return inventory.save(created);
    }

    private InventoryLedgerEntry post(InventoryItem item, String type, BigDecimal delta, String reason,
                                      String lot, LocalDate expiry, UUID relatedId) {
        BigDecimal next;
        try {
            next = InventoryStock.apply(item.getQuantityOnHand(), item.getReservedQuantity(), delta);
        } catch (IllegalArgumentException ex) {
            throw negative();
        }
        item.setQuantityOnHand(next);
        inventory.save(item);
        InventoryLedgerEntry line = new InventoryLedgerEntry();
        line.setTenantId(access.tenantId());
        line.setItemId(item.getId());
        line.setFarmId(item.getFarmId());
        line.setMovementType(type);
        line.setQuantityDelta(delta);
        line.setQuantityAfter(next);
        line.setUnit(item.getUnit());
        line.setReason(reason);
        line.setLotCode(lot);
        line.setExpiresOn(expiry);
        line.setSupplierName(item.getSupplierName());
        line.setStoreName(item.getStoreName());
        line.setRelatedId(relatedId);
        line.setActorId(access.current().id());
        line.setActorName(access.current().fullName());
        line.setOccurredAt(Instant.now());
        audit.activity("INVENTORY", title(type), item.getName() + " · " + plain(delta) + " " + item.getUnit() + " · " + reason, item.getFarmId());
        return ledger.save(line);
    }

    private InventoryResponse toInventory(InventoryItem item, Map<UUID, Farm> farmMap) {
        BigDecimal available = item.getQuantityOnHand().subtract(item.getReservedQuantity());
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
                item.getVersion() == null ? 0 : item.getVersion()
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
}
