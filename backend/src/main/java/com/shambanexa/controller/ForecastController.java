package com.shambanexa.controller;

import com.shambanexa.dto.FarmForecastResponse;
import com.shambanexa.service.ForecastService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/farms/{farmId}/forecast")
public class ForecastController {

    private final ForecastService forecasts;

    public ForecastController(ForecastService forecasts) {
        this.forecasts = forecasts;
    }

    @GetMapping
    FarmForecastResponse forecast(@PathVariable UUID farmId) {
        return forecasts.forFarm(farmId);
    }
}
