package com.shambanexa.dto;

import java.util.UUID;

public record SearchHit(
String type, UUID id, String title, String subtitle
) {
}
