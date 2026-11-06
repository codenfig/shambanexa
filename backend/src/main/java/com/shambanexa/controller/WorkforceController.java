package com.shambanexa.controller;

import com.shambanexa.dto.Workforce;
import com.shambanexa.implementation.WorkforceService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class WorkforceController {
    private final WorkforceService workforce;

    WorkforceController(WorkforceService workforce) {
        this.workforce = workforce;
    }

    @GetMapping("/workforce")
    Workforce.Bundle bundle() {
        return workforce.bundle();
    }

    @GetMapping("/requests")
    List<Workforce.FarmerRequestView> requests() {
        return workforce.requestViews();
    }

    @PostMapping("/requests")
    Workforce.FarmerRequestView createRequest(@RequestBody Workforce.FarmerRequestBody body) {
        return workforce.createRequest(body);
    }

    @PostMapping("/requests/{id}/reply")
    Workforce.FarmerRequestView reply(@PathVariable UUID id, @RequestBody Workforce.ReplyBody body) {
        return workforce.reply(id, body);
    }

    @PostMapping("/staff/attendance")
    Workforce.AttendanceView attendance(@RequestBody Workforce.AttendanceRequest body) {
        return workforce.saveAttendance(body);
    }

    @PostMapping("/staff/shifts")
    Workforce.ShiftView shift(@RequestBody Workforce.ShiftRequest body) {
        return workforce.saveShift(body);
    }

    @PostMapping("/staff/timesheets")
    Workforce.TimesheetView timesheet(@RequestBody Workforce.TimesheetRequest body) {
        return workforce.saveTimesheet(body);
    }

    @PostMapping("/staff/reviews")
    Workforce.ReviewView review(@RequestBody Workforce.ReviewRequest body) {
        return workforce.saveReview(body);
    }

    @PostMapping("/staff/training")
    Workforce.TrainingView training(@RequestBody Workforce.TrainingRequest body) {
        return workforce.saveTraining(body);
    }

    @PostMapping("/staff/goals")
    Workforce.GoalView goal(@RequestBody Workforce.GoalRequest body) {
        return workforce.saveGoal(body);
    }

    @PostMapping("/staff/goals/{id}/done")
    Workforce.GoalView completeGoal(@PathVariable UUID id) {
        return workforce.completeGoal(id);
    }

    @PostMapping("/staff/competencies")
    Workforce.CompetencyView competency(@RequestBody Workforce.CompetencyRequest body) {
        return workforce.saveCompetency(body);
    }
}
