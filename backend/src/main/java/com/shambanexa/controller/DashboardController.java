package com.shambanexa.controller;
import com.shambanexa.dto.ActivityResponse;
import com.shambanexa.dto.DashboardResponse;
import com.shambanexa.service.DashboardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {
    private final DashboardService dashboardService;

    DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping
    DashboardResponse current() {
        return dashboardService.current();
    }

    @GetMapping("/activity")
    List<ActivityResponse> activity() {
        return dashboardService.activity();
    }
}

