package com.shambanexa.dto;

import java.time.Instant;

public record AdviserEndorsement(
        String recommenderName,
        String note,
        Instant createdAt
) {
}
