package com.shambanexa.implementation;

import com.shambanexa.service.AuditTrail;

import com.shambanexa.common.TenantContext;
import com.shambanexa.entity.ActivityEvent;
import com.shambanexa.entity.AuditEvent;
import com.shambanexa.repository.ActivityEventRepository;
import com.shambanexa.repository.AuditEventRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class AuditTrailImpl implements AuditTrail {

    private final AuditEventRepository audits;
    private final ActivityEventRepository activity;

    public AuditTrailImpl(AuditEventRepository audits, ActivityEventRepository activity) {
        this.audits = audits;
        this.activity = activity;
    }

    public void record(String action, String entityType, UUID entityId, String detail) {
        var user = TenantContext.require();
        AuditEvent event = new AuditEvent();
        event.setTenantId(user.tenantId());
        event.setActorId(user.id());
        event.setAction(action);
        event.setEntityType(entityType);
        event.setEntityId(entityId);
        event.setDetail(detail);
        audits.save(event);
    }

    public void activity(String type, String title, String detail, UUID farmId) {
        var user = TenantContext.require();
        ActivityEvent event = new ActivityEvent();
        event.setTenantId(user.tenantId());
        event.setFarmId(farmId);
        event.setEventType(clip(type, 40));
        event.setTitle(clip(title == null || title.isBlank() ? "Activity" : title, 160));
        event.setDetail(clip(detail == null ? "" : detail, 300));
        activity.save(event);
    }

    private String clip(String value, int max) {
        String trimmed = value == null ? "" : value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }
}
