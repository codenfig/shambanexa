package com.shambanexa.dto;

import jakarta.validation.constraints.Size;

public record PhoneRequest(@Size(max = 40) String phone) {
}
