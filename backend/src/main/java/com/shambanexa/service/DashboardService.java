package com.shambanexa.service;
import com.shambanexa.dto.ActivityResponse;
import com.shambanexa.dto.AlertResponse;
import com.shambanexa.dto.DashboardResponse;

import java.util.List;

public interface DashboardService {
    DashboardResponse current();

    List<AlertResponse> openAlerts();

    List<ActivityResponse> activity();
}
