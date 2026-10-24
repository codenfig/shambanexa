package com.shambanexa.implementation;

import com.shambanexa.common.ApiException;
import com.shambanexa.dto.AdviceRequest;
import com.shambanexa.dto.AdviceResponse;
import com.shambanexa.entity.ExpertAdvice;
import com.shambanexa.entity.UserAccount;
import com.shambanexa.repository.ExpertAdviceRepository;
import com.shambanexa.repository.UserAccountRepository;
import com.shambanexa.security.AccessGuard;
import com.shambanexa.service.AdviceService;
import com.shambanexa.service.AuditTrail;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AdviceServiceImpl implements AdviceService {

    private final ExpertAdviceRepository advice;
    private final UserAccountRepository users;
    private final AccessGuard access;
    private final AuditTrail audit;

    public AdviceServiceImpl(ExpertAdviceRepository advice, UserAccountRepository users, AccessGuard access, AuditTrail audit) {
        this.advice = advice;
        this.users = users;
        this.access = access;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<AdviceResponse> list() {
        Map<UUID, UserAccount> people = users.findByTenantIdOrderByFullNameAsc(access.tenantId()).stream()
                .collect(Collectors.toMap(UserAccount::getId, Function.identity()));
        UUID me = access.current().id();
        return advice.findByTenantIdOrderByCreatedAtDesc(access.tenantId()).stream()
                .map(row -> toResponse(row, people, me))
                .toList();
    }

    @Transactional
    public AdviceResponse publish(AdviceRequest request) {
        access.requireAuthority("ADVICE_WRITE");
        String medication = blank(request.medication());
        String note = blank(request.medicationNote());
        if (note != null && medication == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Name the medication before adding a note");
        }
        ExpertAdvice row = new ExpertAdvice();
        row.setTenantId(access.tenantId());
        row.setAuthorId(access.current().id());
        row.setTopic(request.topic().trim());
        row.setAdvice(request.advice().trim());
        row.setMedication(medication);
        row.setMedicationNote(note);
        advice.save(row);
        String detail = row.getTopic() + (medication == null ? "" : " · " + medication);
        audit.record("ADVICE_PUBLISHED", "ADVICE", row.getId(), clip(detail, 500));
        audit.activity("ADVICE", "Advice published", clip(detail, 300), null);
        return toResponse(row, Map.of(row.getAuthorId(), users.findById(row.getAuthorId()).orElseThrow()), row.getAuthorId());
    }

    @Transactional
    public void remove(UUID id) {
        ExpertAdvice row = advice.findById(id)
                .filter(item -> access.tenantId().equals(item.getTenantId()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ADVICE_NOT_FOUND", "Advice not found"));
        if (!row.getAuthorId().equals(access.current().id())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "You do not have permission to do that");
        }
        advice.delete(row);
        audit.record("ADVICE_REMOVED", "ADVICE", id, row.getTopic());
    }

    private AdviceResponse toResponse(ExpertAdvice row, Map<UUID, UserAccount> people, UUID me) {
        UserAccount author = people.get(row.getAuthorId());
        return new AdviceResponse(
                row.getId(),
                row.getTopic(),
                row.getAdvice(),
                row.getMedication(),
                row.getMedicationNote(),
                row.getAuthorId(),
                author == null ? "Farm Adviser" : author.getFullName(),
                row.getCreatedAt(),
                row.getAuthorId().equals(me)
        );
    }

    private String blank(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String clip(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
