package com.shambanexa.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record AdviserResponse(
        UUID id,
        String fullName,
        boolean self,
        boolean allFarms,
        String availability,
        String availabilityNote,
        List<String> talents,
        int assignedFarmCount,
        int openAlerts,
        int openTasks,
        String load,
        BigDecimal ratingAverage,
        int ratingCount,
        Integer myScore,
        String myRatingNote,
        int recommendationCount,
        boolean recommendedByMe,
        String myRecommendationNote,
        List<AdviserEndorsement> recommendations,
        List<AdviserPlace> places
) {
}
