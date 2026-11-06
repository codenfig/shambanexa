package com.shambanexa.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record AlertAckBody(
        @NotNull UUID batchId,
        @NotBlank @Size(max = 40) String alertKind,
        @Size(max = 160) String assigneeName,
        @Size(max = 300) String note
) {
}
