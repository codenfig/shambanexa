package com.shambanexa.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class HealthCenterDtos {
    private HealthCenterDtos() {
    }

    public record DoseBody(
            UUID itemId,
            UUID batchId,
            BigDecimal quantity,
            Integer withdrawalDays,
            String overrideReason
    ) {
    }

    public record TreatmentBody(
            UUID farmId,
            UUID animalId,
            UUID herdId,
            String symptoms,
            String diagnosis,
            String description,
            String veterinarianName,
            String status,
            LocalDate startedOn,
            String outcome,
            LocalDate followUpOn,
            DoseBody dose
    ) {
    }

    public record TreatmentStatusBody(
            String status,
            String outcome,
            LocalDate followUpOn
    ) {
    }

    public record ProtocolBody(
            String speciesName,
            String name,
            String ageGroup,
            Integer intervalDays,
            UUID itemId,
            String notes
    ) {
    }

    public record ProtocolActiveBody(boolean active) {
    }

    public record VaccinationBody(
            UUID farmId,
            UUID animalId,
            UUID herdId,
            UUID protocolId,
            String speciesName,
            String ageGroup,
            String vaccineName,
            UUID itemId,
            UUID batchId,
            BigDecimal quantity,
            Integer withdrawalDays,
            String overrideReason,
            LocalDate administeredOn,
            LocalDate nextDueOn,
            String veterinarianName,
            String notes,
            String status
    ) {
    }

    public record VaccinationStatusBody(
            String status,
            LocalDate administeredOn,
            LocalDate nextDueOn
    ) {
    }

    public record DoseView(
            UUID itemId,
            String itemName,
            String unit,
            UUID batchId,
            String lotCode,
            BigDecimal quantity,
            Integer withdrawalDays,
            LocalDate withdrawalEndsOn,
            boolean issued,
            BigDecimal knownCost
    ) {
    }

    public record TreatmentView(
            UUID id,
            UUID farmId,
            String farmName,
            UUID animalId,
            UUID herdId,
            String subject,
            String speciesName,
            String symptoms,
            String diagnosis,
            String description,
            String veterinarianName,
            String status,
            LocalDate startedOn,
            String outcome,
            LocalDate followUpOn,
            DoseView dose
    ) {
    }

    public record ProtocolView(
            UUID id,
            String speciesName,
            String name,
            String ageGroup,
            int intervalDays,
            UUID itemId,
            String itemName,
            String notes,
            String approvedByName,
            boolean active,
            LocalDate approvedOn
    ) {
    }

    public record VaccinationView(
            UUID id,
            UUID farmId,
            String farmName,
            UUID animalId,
            UUID herdId,
            String subject,
            UUID protocolId,
            String protocolName,
            String speciesName,
            String ageGroup,
            String vaccineName,
            UUID itemId,
            String itemName,
            UUID batchId,
            String lotCode,
            BigDecimal quantity,
            String unit,
            Integer withdrawalDays,
            LocalDate withdrawalEndsOn,
            boolean doseIssued,
            BigDecimal knownCost,
            LocalDate administeredOn,
            LocalDate nextDueOn,
            String veterinarianName,
            String notes,
            String status,
            String displayStatus
    ) {
    }

    public record NamedCount(String name, long count) {
    }

    public record MedicineUse(String name, String unit, BigDecimal quantity, BigDecimal knownCost) {
    }

    public record Report(
            long inProgress,
            long completedTreatments,
            long plannedVaccinations,
            long dueSoon,
            long overdue,
            long missed,
            long completedVaccinations,
            BigDecimal knownCost,
            int dosesWithoutCost,
            List<NamedCount> conditions,
            List<MedicineUse> medicines,
            List<NamedCount> veterinarians,
            String reminderNote
    ) {
    }

    public record Board(
            List<TreatmentView> treatments,
            List<VaccinationView> vaccinations,
            List<ProtocolView> protocols,
            Report report,
            boolean canRecord,
            boolean canApproveProtocols
    ) {
    }
}
