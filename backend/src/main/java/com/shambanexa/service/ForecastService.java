package com.shambanexa.service;

import com.shambanexa.dto.FarmForecastResponse;

import java.util.UUID;

public interface ForecastService {
    FarmForecastResponse forFarm(UUID farmId);
}
