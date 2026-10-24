package com.shambanexa.dto;

import java.util.UUID;

public record SpeciesResponse(
UUID id, String code, String name, String category, String chartColor
) {
}
