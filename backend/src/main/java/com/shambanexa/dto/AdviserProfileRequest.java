package com.shambanexa.dto;

import java.util.List;

public record AdviserProfileRequest(
        List<String> talents,
        String availability,
        String availabilityNote
) {
}
