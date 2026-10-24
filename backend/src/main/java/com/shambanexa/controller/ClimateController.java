package com.shambanexa.controller;

import com.shambanexa.dto.FarmClimateResponse;
import com.shambanexa.service.ClimateService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/farms/{farmId}/climate")
public class ClimateController {

    private final ClimateService climate;

    public ClimateController(ClimateService climate) {
        this.climate = climate;
    }

    @GetMapping
    FarmClimateResponse climate(@PathVariable UUID farmId) {
        return climate.forFarm(farmId);
    }
}
