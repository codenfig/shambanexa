package com.shambanexa.dto;

import java.util.UUID;

public record BreedResponse(
UUID id, String name, UUID speciesId
) {
}
