package com.shambanexa.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class MasterDtos {
    private MasterDtos() {
    }

    public record CategoryView(String code, String label, String description, long total, long active) {
    }

    public record RecordView(
            UUID id,
            String code,
            String name,
            String status,
            String origin,
            String groupLabel,
            UUID parentId,
            String chartColor,
            Integer sortOrder,
            Boolean individualTracking,
            long usage,
            String usageLabel,
            boolean codeLocked,
            Instant createdAt,
            Instant updatedAt,
            String createdByName,
            String updatedByName,
            String detail,
            Integer numericValue
    ) {
    }

    public record PageView(String category, String label, long total, int page, int size, boolean canEdit, List<RecordView> records) {
    }

    public record HistoryLine(String action, String detail, Instant recordedAt, String actorName) {
    }

    public record DetailView(RecordView record, List<HistoryLine> history) {
    }

    public record SpeciesBody(
            @NotBlank @Size(max = 40) String code,
            @NotBlank @Size(max = 80) String name,
            @NotBlank @Size(max = 40) String category,
            @NotBlank @Pattern(regexp = "#[0-9A-Fa-f]{6}") String chartColor,
            @NotNull @Min(0) @Max(999) Integer sortOrder,
            @NotNull Boolean individualTracking
    ) {
    }

    public record BreedBody(
            @NotNull UUID speciesId,
            @NotBlank @Size(max = 40) String code,
            @NotBlank @Size(max = 120) String name
    ) {
    }

    public record StatusBody(@NotNull Boolean active) {
    }

    public record ReferenceBody(
            @NotBlank @Size(max = 40) String code,
            @NotBlank @Size(max = 160) String name,
            UUID parentId,
            @Size(max = 400) String detail,
            Integer numericValue,
            @Min(0) @Max(999) Integer sortOrder
    ) {
    }

    public record ImportRow(int line, String code, String name, String action, String message) {
    }

    public record ImportPreview(int created, int updated, int skipped, int failed, List<ImportRow> rows) {
    }
}
