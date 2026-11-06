package com.shambanexa.service;

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

import java.util.List;
import java.util.UUID;

public interface InventoryService {
    List<InventoryResponse> inventory();

    InventoryResponse createInventory(InventoryRequest request);

    InventoryResponse receive(UUID id, StockReceiptRequest request);

    InventoryResponse issue(UUID id, StockIssueRequest request);

    InventoryResponse transfer(UUID id, StockTransferRequest request);

    InventoryAdjustmentResponse adjust(UUID id, AdjustRequest request);

    InventoryAdjustmentResponse decide(UUID id, AdjustmentDecisionRequest request);

    List<InventoryMovementResponse> ledger();

    List<InventoryAdjustmentResponse> adjustments();

    List<StockBatchView> batches();

    StockBatchView fefo(UUID itemId);

    List<StockAlertView> alerts();

    StockAlertView acknowledge(AlertAckBody body);

    List<SupplierRecord> supplierRecords();

    SupplierRecord saveSupplier(SupplierBody body);

    InventoryReportView report();
}
