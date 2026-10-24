package com.shambanexa.service;

import com.shambanexa.dto.AnimalRequest;
import com.shambanexa.dto.AnimalResponse;
import com.shambanexa.dto.BatchQuantityRequest;
import com.shambanexa.dto.BatchResponse;
import com.shambanexa.dto.PageResponse;
import com.shambanexa.dto.TransferRequest;

import java.util.List;
import java.util.UUID;

public interface AnimalService {
    PageResponse<AnimalResponse> list(UUID farmId, UUID speciesId, String q, int page, int size);

    List<BatchResponse> batches();

    BatchResponse updateQuantity(UUID id, BatchQuantityRequest request);

    AnimalResponse create(AnimalRequest request);

    AnimalResponse transfer(UUID id, TransferRequest request);

    AnimalResponse get(UUID id);
}
