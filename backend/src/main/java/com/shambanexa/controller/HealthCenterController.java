package com.shambanexa.controller;

import com.shambanexa.dto.HealthCenterDtos.Board;
import com.shambanexa.dto.HealthCenterDtos.ProtocolActiveBody;
import com.shambanexa.dto.HealthCenterDtos.ProtocolBody;
import com.shambanexa.dto.HealthCenterDtos.ProtocolView;
import com.shambanexa.dto.HealthCenterDtos.TreatmentBody;
import com.shambanexa.dto.HealthCenterDtos.TreatmentStatusBody;
import com.shambanexa.dto.HealthCenterDtos.TreatmentView;
import com.shambanexa.dto.HealthCenterDtos.VaccinationBody;
import com.shambanexa.dto.HealthCenterDtos.VaccinationStatusBody;
import com.shambanexa.dto.HealthCenterDtos.VaccinationView;
import com.shambanexa.implementation.HealthCenterService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/health-center")
public class HealthCenterController {

    private final HealthCenterService health;

    public HealthCenterController(HealthCenterService health) {
        this.health = health;
    }

    @GetMapping
    Board board() {
        return health.board();
    }

    @PostMapping("/treatments")
    TreatmentView recordTreatment(@RequestBody TreatmentBody body) {
        return health.recordTreatment(body);
    }

    @PutMapping("/treatments/{id}")
    TreatmentView updateTreatment(@PathVariable UUID id, @RequestBody TreatmentStatusBody body) {
        return health.updateTreatment(id, body);
    }

    @PostMapping("/protocols")
    ProtocolView saveProtocol(@RequestBody ProtocolBody body) {
        return health.saveProtocol(body);
    }

    @PutMapping("/protocols/{id}")
    ProtocolView setProtocolActive(@PathVariable UUID id, @RequestBody ProtocolActiveBody body) {
        return health.setProtocolActive(id, body);
    }

    @PostMapping("/vaccinations")
    VaccinationView recordVaccination(@RequestBody VaccinationBody body) {
        return health.recordVaccination(body);
    }

    @PutMapping("/vaccinations/{id}")
    VaccinationView updateVaccination(@PathVariable UUID id, @RequestBody VaccinationStatusBody body) {
        return health.updateVaccination(id, body);
    }
}
