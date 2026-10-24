package com.shambanexa.service;

import com.shambanexa.dto.AdviserProfileRequest;
import com.shambanexa.dto.AdviserRecommendationRequest;
import com.shambanexa.dto.AdviserRatingRequest;
import com.shambanexa.dto.AdviserResponse;

import java.util.List;
import java.util.UUID;

public interface AdviserDirectoryService {
    List<AdviserResponse> list(String talent, String availability, String load);

    AdviserResponse updateProfile(AdviserProfileRequest request);

    AdviserResponse rate(UUID adviserId, AdviserRatingRequest request);

    AdviserResponse recommend(UUID adviserId, AdviserRecommendationRequest request);

    AdviserResponse withdrawRecommendation(UUID adviserId);
}
