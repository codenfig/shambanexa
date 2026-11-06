package com.shambanexa.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public final class Workforce {
    private Workforce() {
    }

    public record AttendanceRequest(UUID staffId, LocalDate workDate, String mark) {
    }

    public record ShiftRequest(UUID staffId, short weekday, LocalTime starts, LocalTime ends) {
    }

    public record TimesheetRequest(UUID staffId, LocalDate workDate, BigDecimal hours, String note) {
    }

    public record ReviewRequest(UUID staffId, BigDecimal score, LocalDate reviewedOn, String notes) {
    }

    public record TrainingRequest(UUID staffId, String title, LocalDate completedOn) {
    }

    public record GoalRequest(UUID staffId, String title) {
    }

    public record CompetencyRequest(UUID staffId, String skill, String level) {
    }

    public record FarmerRequestBody(UUID farmId, String subject, String message) {
    }

    public record ReplyBody(String reply) {
    }

    public record FarmerRequestView(
            UUID id, UUID farmId, String farmName, String authorName, String subject, String message,
            String status, String reply, String repliedBy, Instant createdAt, Instant answeredAt
    ) {
    }

    public record AttendanceView(UUID id, UUID staffId, String staffName, UUID farmId, String farmName, LocalDate workDate, String mark) {
    }

    public record ShiftView(UUID id, UUID staffId, String staffName, UUID farmId, String farmName, short weekday, LocalTime starts, LocalTime ends) {
    }

    public record TimesheetView(UUID id, UUID staffId, String staffName, UUID farmId, String farmName, LocalDate workDate, BigDecimal hours, String note) {
    }

    public record ReviewView(UUID id, UUID staffId, String staffName, UUID farmId, String farmName, BigDecimal score, LocalDate reviewedOn, String notes, String reviewerName) {
    }

    public record TrainingView(UUID id, UUID staffId, String staffName, UUID farmId, String farmName, String title, LocalDate completedOn) {
    }

    public record GoalView(UUID id, UUID staffId, String staffName, UUID farmId, String farmName, String title, String status) {
    }

    public record CompetencyView(UUID id, UUID staffId, String staffName, UUID farmId, String farmName, String skill, String level) {
    }

    public record Bundle(
            List<FarmerRequestView> requests,
            List<AttendanceView> attendance,
            List<ShiftView> shifts,
            List<TimesheetView> timesheets,
            List<ReviewView> reviews,
            List<TrainingView> training,
            List<GoalView> goals,
            List<CompetencyView> competencies
    ) {
    }
}
