package com.shambanexa.service;

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

import java.util.List;
import java.util.UUID;

public interface OperationsService {
    List<ProductionResponse> production();

    ProductionResponse recordProduction(ProductionRequest request);

    List<StaffResponse> staff();

    StaffResponse createStaff(StaffRequest request);

    List<SpeciesResponse> species();

    List<BreedResponse> breeds(UUID speciesId);

    List<TaskResponse> tasks();

    List<FishCycleResponse> fishCycles();

    List<HealthCaseResponse> healthCases();

    DiseaseReportResponse reportDisease(DiseaseReportRequest request);

    AnimalHealthResponse reportAnimalHealth(AnimalHealthRequest request);

    SettingsResponse settings();

    SettingsResponse updateSettings(SettingsUpdateRequest request);

    List<SearchHit> search(String q);

    String dashboardCsv(com.shambanexa.dto.DashboardResponse dashboard);
}
