package com.shambanexa.dto;

import java.time.LocalDate;

public record SubscriptionView(
String plan, LocalDate validUntil
) {
}
