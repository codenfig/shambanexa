package com.shambanexa.controller;
import com.shambanexa.dto.AdjustRequest;
import com.shambanexa.dto.BatchQuantityRequest;
import com.shambanexa.dto.BatchResponse;
import com.shambanexa.dto.BreedResponse;
import com.shambanexa.dto.AnimalHealthRequest;
import com.shambanexa.dto.AnimalHealthResponse;
import com.shambanexa.dto.DashboardResponse;
import com.shambanexa.dto.DiseaseReportRequest;
import com.shambanexa.dto.DiseaseReportResponse;
import com.shambanexa.dto.FishCycleResponse;
import com.shambanexa.dto.HealthCaseResponse;
import com.shambanexa.dto.AdjustmentDecisionRequest;
import com.shambanexa.dto.InventoryAdjustmentResponse;
import com.shambanexa.dto.InventoryMovementResponse;
import com.shambanexa.dto.InventoryRequest;
import com.shambanexa.dto.InventoryResponse;
import com.shambanexa.dto.StockIssueRequest;
import com.shambanexa.dto.StockReceiptRequest;
import com.shambanexa.dto.StockTransferRequest;
import com.shambanexa.dto.ProductionRequest;
import com.shambanexa.dto.ProductionResponse;
import com.shambanexa.dto.SearchHit;
import com.shambanexa.dto.SettingsResponse;
import com.shambanexa.dto.SettingsUpdateRequest;
import com.shambanexa.dto.SpeciesResponse;
import com.shambanexa.dto.StaffRequest;
import com.shambanexa.dto.StaffResponse;
import com.shambanexa.dto.TaskResponse;
import com.shambanexa.service.AnimalService;
import com.shambanexa.service.DashboardService;
import com.shambanexa.service.InventoryService;
import com.shambanexa.service.OperationsService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class CatalogController {
    private final AnimalService animals;
    private final OperationsService operations;
    private final DashboardService dashboard;
    private final InventoryService inventory;

    CatalogController(AnimalService animals, OperationsService operations, DashboardService dashboard, InventoryService inventory) {
        this.animals = animals;
        this.operations = operations;
        this.dashboard = dashboard;
        this.inventory = inventory;
    }

    @GetMapping("/batches")
    List<BatchResponse> batches() {
        return animals.batches();
    }

    @PatchMapping("/batches/{id}")
    BatchResponse updateBatch(@PathVariable UUID id, @Valid @RequestBody BatchQuantityRequest request) {
        return animals.updateQuantity(id, request);
    }

    @GetMapping("/species")
    List<SpeciesResponse> species() {
        return operations.species();
    }

    @GetMapping("/breeds")
    List<BreedResponse> breeds(@RequestParam UUID speciesId) {
        return operations.breeds(speciesId);
    }

    @GetMapping("/production")
    List<ProductionResponse> production() {
        return operations.production();
    }

    @PostMapping("/production")
    ProductionResponse recordProduction(@Valid @RequestBody ProductionRequest request) {
        return operations.recordProduction(request);
    }

    @GetMapping("/inventory")
    List<InventoryResponse> inventory() {
        return inventory.inventory();
    }

    @GetMapping("/inventory/ledger")
    List<InventoryMovementResponse> inventoryLedger() {
        return inventory.ledger();
    }

    @GetMapping("/inventory/adjustments")
    List<InventoryAdjustmentResponse> inventoryAdjustments() {
        return inventory.adjustments();
    }

    @PostMapping("/inventory")
    InventoryResponse createInventory(@Valid @RequestBody InventoryRequest request) {
        return inventory.createInventory(request);
    }

    @PostMapping("/inventory/{id}/receipts")
    InventoryResponse receive(@PathVariable UUID id, @Valid @RequestBody StockReceiptRequest request) {
        return inventory.receive(id, request);
    }

    @PostMapping("/inventory/{id}/issues")
    InventoryResponse issue(@PathVariable UUID id, @Valid @RequestBody StockIssueRequest request) {
        return inventory.issue(id, request);
    }

    @PostMapping("/inventory/{id}/transfers")
    InventoryResponse transfer(@PathVariable UUID id, @Valid @RequestBody StockTransferRequest request) {
        return inventory.transfer(id, request);
    }

    @PostMapping("/inventory/{id}/adjustments")
    InventoryAdjustmentResponse adjust(@PathVariable UUID id, @Valid @RequestBody AdjustRequest request) {
        return inventory.adjust(id, request);
    }

    @PostMapping("/inventory/adjustments/{id}/decision")
    InventoryAdjustmentResponse decideAdjustment(@PathVariable UUID id, @Valid @RequestBody AdjustmentDecisionRequest request) {
        return inventory.decide(id, request);
    }

    @GetMapping("/staff")
    List<StaffResponse> staff() {
        return operations.staff();
    }

    @PostMapping("/staff")
    StaffResponse createStaff(@Valid @RequestBody StaffRequest request) {
        return operations.createStaff(request);
    }

    @GetMapping("/alerts")
    List<com.shambanexa.dto.AlertResponse> alerts() {
        return dashboard.openAlerts();
    }

    @GetMapping("/tasks")
    List<TaskResponse> tasks() {
        return operations.tasks();
    }

    @GetMapping("/fish-cycles")
    List<FishCycleResponse> fishCycles() {
        return operations.fishCycles();
    }

    @GetMapping("/health-cases")
    List<HealthCaseResponse> healthCases() {
        return operations.healthCases();
    }

    @PostMapping("/disease-reports")
    DiseaseReportResponse reportDisease(@Valid @RequestBody DiseaseReportRequest request) {
        return operations.reportDisease(request);
    }

    @PostMapping("/health-reports")
    AnimalHealthResponse reportAnimalHealth(@Valid @RequestBody AnimalHealthRequest request) {
        return operations.reportAnimalHealth(request);
    }

    @GetMapping("/settings")
    SettingsResponse settings() {
        return operations.settings();
    }

    @PutMapping("/settings")
    SettingsResponse updateSettings(@Valid @RequestBody SettingsUpdateRequest request) {
        return operations.updateSettings(request);
    }

    @GetMapping("/search")
    List<SearchHit> search(@RequestParam String q) {
        return operations.search(q);
    }

    @GetMapping(value = "/reports/dashboard.csv", produces = "text/csv")
    ResponseEntity<byte[]> export() {
        DashboardResponse current = dashboard.current();
        byte[] body = operations.dashboardCsv(current).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=shambanexa-dashboard.csv")
                .contentType(new MediaType("text", "csv"))
                .body(body);
    }
}
