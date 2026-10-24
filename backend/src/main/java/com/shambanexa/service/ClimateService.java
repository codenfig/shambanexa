package com.shambanexa.service;

import com.shambanexa.dto.FarmClimateResponse;

import java.util.UUID;

public interface ClimateService {
    FarmClimateResponse forFarm(UUID farmId);
}
