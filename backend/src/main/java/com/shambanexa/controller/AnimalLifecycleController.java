package com.shambanexa.controller;

import com.shambanexa.common.ApiException;
import com.shambanexa.entity.Animal;
import com.shambanexa.repository.AnimalRepository;
import com.shambanexa.security.AccessGuard;
import com.shambanexa.service.AuditTrail;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.*;

/** Append-only animal history. All reads and writes enforce tenant and farm scope. */
@RestController
@RequestMapping("/api/v1/animals/{animalId}")
public class AnimalLifecycleController {
    private final AnimalRepository animals;
    private final AccessGuard access;
    private final JdbcTemplate jdbc;
    private final AuditTrail audit;

    public AnimalLifecycleController(AnimalRepository animals, AccessGuard access, JdbcTemplate jdbc, AuditTrail audit) {
        this.animals = animals;
        this.access = access;
        this.jdbc = jdbc;
        this.audit = audit;
    }

    private Animal visible(UUID id) {
        Animal animal = animals.findByIdAndTenantId(id, access.tenantId())
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ANIMAL_NOT_FOUND", "Animal not found"));
        access.checkFarm(animal.getFarmId());
        return animal;
    }

    public record WeightInput(@NotNull @DecimalMin("0.001") @DecimalMax("99999.999") BigDecimal weightKg,
                              @NotNull @PastOrPresent LocalDate measuredOn,
                              @Size(max=500) String notes) {}
    public record BreedingInput(@NotBlank String eventType,
                                @NotNull @PastOrPresent LocalDate eventOn,
                                LocalDate expectedBirthOn,
                                UUID partnerAnimalId,
                                @Min(0) Integer offspringCount,
                                @Size(max=500) String notes) {}

    @GetMapping("/weights")
    @Transactional(readOnly=true)
    public List<Map<String,Object>> weights(@PathVariable UUID animalId) {
        visible(animalId);
        return jdbc.queryForList("""
            SELECT id, measured_on, weight_kg, notes, created_at
            FROM animal_weights WHERE tenant_id=? AND animal_id=?
            ORDER BY measured_on DESC, created_at DESC LIMIT 500
            """, access.tenantId(), animalId);
    }

    @PostMapping("/weights")
    @Transactional
    public Map<String,Object> addWeight(@PathVariable UUID animalId, @Valid @RequestBody WeightInput input) {
        visible(animalId);
        access.requireAuthority("ANIMAL_WRITE");
        UUID id = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO animal_weights(id,tenant_id,animal_id,measured_on,weight_kg,notes,recorded_by)
            VALUES(?,?,?,?,?,?,?)
            """, id, access.tenantId(), animalId, Date.valueOf(input.measuredOn()), input.weightKg(),
                input.notes(), access.current().id());
        audit.record("ANIMAL_WEIGHT_RECORDED", "ANIMAL", animalId, "Weight measurement recorded on " + input.measuredOn());
        return Map.of("id",id,"measuredOn",input.measuredOn(),"weightKg",input.weightKg());
    }

    @GetMapping("/breeding")
    @Transactional(readOnly=true)
    public List<Map<String,Object>> breeding(@PathVariable UUID animalId) {
        visible(animalId);
        return jdbc.queryForList("""
            SELECT id,event_type,event_on,expected_birth_on,partner_animal_id,offspring_count,notes,created_at
            FROM animal_breeding_events WHERE tenant_id=? AND animal_id=?
            ORDER BY event_on DESC,created_at DESC LIMIT 500
            """,access.tenantId(),animalId);
    }

    @PostMapping("/breeding")
    @Transactional
    public Map<String,Object> addBreeding(@PathVariable UUID animalId, @Valid @RequestBody BreedingInput input) {
        Animal animal = visible(animalId);
        access.requireAuthority("ANIMAL_WRITE");
        Set<String> types = Set.of("MATING","INSEMINATION","PREGNANCY_CHECK","PREGNANCY_CONFIRMED","BIRTH","LOSS","WEANING");
        String type = input.eventType().trim().toUpperCase(Locale.ROOT);
        if (!types.contains(type)) throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_EVENT","Invalid breeding event");
        if (input.expectedBirthOn()!=null && input.expectedBirthOn().isBefore(input.eventOn()))
            throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_DATE","Expected birth cannot precede event");
        if (input.partnerAnimalId()!=null) {
            Animal partner = visible(input.partnerAnimalId());
            if (!animal.getSpeciesId().equals(partner.getSpeciesId()))
                throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_PARTNER","Breeding partner must be the same species");
        }
        UUID id=UUID.randomUUID();
        jdbc.update("""
            INSERT INTO animal_breeding_events(id,tenant_id,animal_id,event_type,event_on,expected_birth_on,
                partner_animal_id,offspring_count,notes,recorded_by) VALUES(?,?,?,?,?,?,?,?,?,?)
            """,id,access.tenantId(),animalId,type,Date.valueOf(input.eventOn()),
            input.expectedBirthOn()==null?null:Date.valueOf(input.expectedBirthOn()),
            input.partnerAnimalId(),input.offspringCount(),input.notes(),access.current().id());
        audit.record("ANIMAL_BREEDING_RECORDED", "ANIMAL", animalId, type + " on " + input.eventOn());
        return Map.of("id",id,"eventType",type,"eventOn",input.eventOn());
    }

    @GetMapping("/treatment-history")
    @Transactional(readOnly=true)
    public List<Map<String,Object>> treatmentHistory(@PathVariable UUID animalId) {
        visible(animalId);
        return jdbc.queryForList("""
            SELECT t.id,t.started_on,t.diagnosis,t.treatment_status,t.veterinarian_name,
                   d.item_id,d.batch_id,d.quantity,d.withdrawal_days,d.withdrawal_ends_on,
                   d.issued
            FROM animal_treatments t LEFT JOIN treatment_doses d
              ON d.treatment_id=t.id AND d.tenant_id=t.tenant_id
            WHERE t.tenant_id=? AND t.animal_id=?
            ORDER BY t.started_on DESC,t.created_at DESC LIMIT 500
            """,access.tenantId(),animalId);
    }

    @GetMapping("/withdrawal-status")
    @Transactional(readOnly=true)
    public Map<String,Object> withdrawalStatus(@PathVariable UUID animalId) {
        visible(animalId);
        LocalDate today=LocalDate.now(java.time.ZoneId.of("Africa/Dar_es_Salaam"));
        List<Map<String,Object>> active=jdbc.queryForList("""
            SELECT d.treatment_id,d.batch_id,d.withdrawal_ends_on
            FROM treatment_doses d JOIN animal_treatments t ON t.id=d.treatment_id
            WHERE t.tenant_id=? AND t.animal_id=? AND d.tenant_id=?
              AND d.issued=true AND d.withdrawal_ends_on>=?
            UNION ALL
            SELECT v.id,v.batch_id,v.withdrawal_ends_on
            FROM animal_vaccinations v
            WHERE v.tenant_id=? AND v.animal_id=? AND v.dose_issued=true
              AND v.withdrawal_ends_on>=?
            """,access.tenantId(),animalId,access.tenantId(),Date.valueOf(today),
            access.tenantId(),animalId,Date.valueOf(today));
        return Map.of("activeWithdrawal",!active.isEmpty(),"asOf",today,"records",active);
    }
}
