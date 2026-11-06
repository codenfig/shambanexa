package com.shambanexa.controller;

import com.shambanexa.dto.MedicalDtos.Board;
import com.shambanexa.dto.MedicalDtos.MedicineBody;
import com.shambanexa.dto.MedicalDtos.MedicineView;
import com.shambanexa.dto.MedicalDtos.NoteBody;
import com.shambanexa.dto.MedicalDtos.OrderBody;
import com.shambanexa.dto.MedicalDtos.RequestView;
import com.shambanexa.dto.MedicalDtos.ShopBody;
import com.shambanexa.dto.MedicalDtos.SupplierView;
import com.shambanexa.implementation.MedicalSupplierService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/medical-suppliers")
public class MedicalSupplierController {

    private final MedicalSupplierService medical;

    public MedicalSupplierController(MedicalSupplierService medical) {
        this.medical = medical;
    }

    @GetMapping
    Board board(@RequestParam(required = false) String q,
                @RequestParam(required = false) String animal,
                @RequestParam(required = false) String category,
                @RequestParam(required = false) String region,
                @RequestParam(required = false) String district) {
        return medical.board(q, animal, category, region, district);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    SupplierView register(@RequestBody ShopBody body) {
        return medical.register(body);
    }

    @PutMapping("/{id}")
    SupplierView update(@PathVariable UUID id, @RequestBody ShopBody body) {
        return medical.update(id, body);
    }

    @PostMapping("/{id}/verification")
    SupplierView verify(@PathVariable UUID id, @RequestBody NoteBody body) {
        return medical.verify(id, body);
    }

    @PostMapping("/{id}/medicines")
    @ResponseStatus(HttpStatus.CREATED)
    MedicineView addMedicine(@PathVariable UUID id, @RequestBody MedicineBody body) {
        return medical.addMedicine(id, body);
    }

    @PutMapping("/{id}/medicines/{medicineId}")
    MedicineView updateMedicine(@PathVariable UUID id, @PathVariable UUID medicineId, @RequestBody MedicineBody body) {
        return medical.updateMedicine(id, medicineId, body);
    }

    @PostMapping("/requests")
    @ResponseStatus(HttpStatus.CREATED)
    RequestView order(@RequestBody OrderBody body) {
        return medical.order(body);
    }

    @PostMapping("/requests/{id}/prescription")
    RequestView prescribe(@PathVariable UUID id, @RequestBody NoteBody body) {
        return medical.prescribe(id, body);
    }

    @PostMapping("/requests/{id}/decision")
    RequestView decide(@PathVariable UUID id, @RequestBody NoteBody body) {
        return medical.decide(id, body);
    }
}
