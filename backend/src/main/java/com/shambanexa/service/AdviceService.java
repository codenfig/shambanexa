package com.shambanexa.service;

import com.shambanexa.dto.AdviceRequest;
import com.shambanexa.dto.AdviceResponse;

import java.util.List;
import java.util.UUID;

public interface AdviceService {
    List<AdviceResponse> list();

    AdviceResponse publish(AdviceRequest request);

    void remove(UUID id);
}
