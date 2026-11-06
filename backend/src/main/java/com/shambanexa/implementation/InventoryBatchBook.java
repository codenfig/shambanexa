package com.shambanexa.implementation;

import com.shambanexa.entity.InventoryBatch;
import com.shambanexa.entity.InventoryItem;
import com.shambanexa.entity.InventoryReference;
import com.shambanexa.repository.InventoryBatchRepository;
import com.shambanexa.repository.InventoryReferenceRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class InventoryBatchBook {

    public static final ZoneId ZONE = ZoneId.of("Africa/Dar_es_Salaam");

    private final InventoryBatchRepository batches;
    private final InventoryReferenceRepository references;

    public InventoryBatchBook(InventoryBatchRepository batches, InventoryReferenceRepository references) {
        this.batches = batches;
        this.references = references;
    }

    public LocalDate today() {
        return LocalDate.now(ZONE);
    }

    public int windowDays(UUID tenantId) {
        return references.findByTenantIdAndKindOrderBySortOrderAscNameAsc(tenantId, "INV_WINDOW").stream()
                .filter(row -> "ACTIVE".equals(row.getStatus()) && "primary".equalsIgnoreCase(row.getDetail()))
                .map(InventoryReference::getNumericValue)
                .filter(value -> value != null && value > 0)
                .findFirst()
                .orElse(30);
    }

    public List<InventoryBatch> all(UUID tenantId) {
        return batches.findByTenantIdOrderByExpiresOnAsc(tenantId);
    }

    public List<InventoryBatch> of(UUID tenantId, UUID itemId) {
        return batches.findByTenantIdAndItemIdOrderByExpiresOnAsc(tenantId, itemId);
    }

    public InventoryBatch require(UUID tenantId, UUID id) {
        return batches.findByIdAndTenantId(id, tenantId).orElse(null);
    }

    public InventoryBatch fefo(UUID tenantId, UUID itemId, LocalDate today) {
        return InventoryExpiry.earliest(of(tenantId, itemId), today);
    }

    public BigDecimal usable(List<InventoryBatch> rows, LocalDate today) {
        return rows.stream()
                .filter(batch -> InventoryExpiry.usable(batch, today))
                .map(InventoryBatch::getQuantityRemaining)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public InventoryBatch prepare(InventoryItem item, String lot, LocalDate manufactured, LocalDate expires,
                                  LocalDate bestBefore, LocalDate received, Integer openDays, String supplierName,
                                  UUID supplierId, String store, BigDecimal unitCost, boolean quarantine) {
        String code = lot == null || lot.isBlank() ? "OPENING" : lot.trim().toUpperCase(Locale.ROOT);
        InventoryBatch batch = of(item.getTenantId(), item.getId()).stream()
                .filter(row -> row.getLotCode().equalsIgnoreCase(code) && !"DISPOSED".equals(row.getStatus()))
                .findFirst()
                .orElseGet(() -> fresh(item, code, manufactured, expires, bestBefore, received, openDays, supplierName, supplierId, store, unitCost));
        if (quarantine) {
            batch.setStatus("QUARANTINED");
        }
        if (expires != null) {
            batch.setExpiresOn(expires);
        }
        if (bestBefore != null) {
            batch.setBestBeforeOn(bestBefore);
        }
        if (manufactured != null) {
            batch.setManufacturedOn(manufactured);
        }
        if (received != null) {
            batch.setReceivedOn(received);
        }
        if (store != null) {
            batch.setStoreName(store);
        }
        if (supplierName != null) {
            batch.setSupplierName(supplierName);
            batch.setSupplierId(supplierId);
        }
        if (unitCost != null) {
            batch.setUnitCost(unitCost);
        }
        if (openDays != null) {
            batch.setOpenDays(openDays);
        }
        return batches.save(batch);
    }

    public void change(InventoryBatch batch, BigDecimal delta, boolean allowBlocked, LocalDate today) {
        if ("DISPOSED".equals(batch.getStatus())) {
            throw new IllegalStateException("disposed");
        }
        if (delta.signum() < 0 && !allowBlocked && !InventoryExpiry.usable(batch, today)) {
            throw new IllegalStateException("blocked");
        }
        BigDecimal next = batch.getQuantityRemaining().add(delta);
        if (next.signum() < 0) {
            throw new IllegalArgumentException("negative");
        }
        if (delta.signum() > 0) {
            batch.setQuantityReceived(batch.getQuantityReceived().add(delta));
        }
        batch.setQuantityRemaining(next);
        batches.save(batch);
    }

    public void mark(InventoryBatch batch, String status) {
        batch.setStatus(status);
        batches.save(batch);
    }

    public BigDecimal physical(UUID tenantId, UUID itemId) {
        return of(tenantId, itemId).stream()
                .filter(batch -> !"DISPOSED".equals(batch.getStatus()))
                .map(InventoryBatch::getQuantityRemaining)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public void refreshDisplay(InventoryItem item, LocalDate today) {
        List<InventoryBatch> rows = of(item.getTenantId(), item.getId());
        InventoryBatch shown = InventoryExpiry.earliest(rows, today);
        if (shown == null) {
            shown = rows.stream().filter(batch -> !"DISPOSED".equals(batch.getStatus())).findFirst().orElse(null);
        }
        if (shown != null) {
            item.setLotCode(shown.getLotCode());
            item.setExpiresOn(shown.getExpiresOn());
            item.setBestBeforeOn(shown.getBestBeforeOn());
            if (shown.getStoreName() != null) {
                item.setStoreName(shown.getStoreName());
            }
        }
    }

    private InventoryBatch fresh(InventoryItem item, String lot, LocalDate manufactured, LocalDate expires,
                                 LocalDate bestBefore, LocalDate received, Integer openDays, String supplierName,
                                 UUID supplierId, String store, BigDecimal unitCost) {
        InventoryBatch created = new InventoryBatch();
        created.setTenantId(item.getTenantId());
        created.setItemId(item.getId());
        created.setFarmId(item.getFarmId());
        created.setLotCode(lot);
        created.setQuantityReceived(BigDecimal.ZERO);
        created.setQuantityRemaining(BigDecimal.ZERO);
        created.setManufacturedOn(manufactured);
        created.setReceivedOn(received == null ? today() : received);
        created.setExpiresOn(expires);
        created.setBestBeforeOn(bestBefore);
        created.setOpenDays(openDays == null ? item.getOpenDays() : openDays);
        created.setSupplierId(supplierId);
        created.setSupplierName(supplierName == null ? item.getSupplierName() : supplierName);
        created.setStoreName(store == null ? item.getStoreName() : store);
        created.setUnitCost(unitCost == null ? item.getUnitCost() : unitCost);
        created.setStatus("OPEN");
        created.setCreatedAt(Instant.now());
        return created;
    }
}
