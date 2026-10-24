package com.shambanexa.dto;

import java.util.List;

public record FarmDetail(
FarmResponse farm, List<FacilityResponse> facilities
) {
}
