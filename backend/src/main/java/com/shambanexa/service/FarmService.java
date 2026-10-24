package com.shambanexa.service;
import com.shambanexa.dto.FarmDetail;
import com.shambanexa.dto.FarmRequest;
import com.shambanexa.dto.FarmResponse;
import com.shambanexa.entity.Farm;

import java.util.List;
import java.util.UUID;

public interface FarmService {
    List<FarmResponse> list();

    FarmDetail detail(UUID id);

    FarmResponse create(FarmRequest request);

    FarmResponse update(UUID id, FarmRequest request);

    List<Farm> visibleFarms();

    Farm load(UUID id);
}
