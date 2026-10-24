package com.shambanexa.service;

import java.util.Map;
import java.util.UUID;

public interface PopulationService {
    long total();

    Map<UUID, Long> byFarm();

    Map<UUID, Long> bySpecies();

    boolean reconciled();
}
