package com.shambanexa.service;

import java.util.UUID;

public interface AuditTrail {
    void record(String action, String entityType, UUID entityId, String detail);

    void activity(String type, String title, String detail, UUID farmId);
}
