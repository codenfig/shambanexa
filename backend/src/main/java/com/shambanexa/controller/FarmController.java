package com.shambanexa.controller;
import com.shambanexa.dto.FarmDetail;
import com.shambanexa.dto.FarmRequest;
import com.shambanexa.dto.FarmResponse;
import com.shambanexa.service.FarmService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/farms")
public class FarmController {
    private final FarmService farms;

    FarmController(FarmService farms) {
        this.farms = farms;
    }

    @GetMapping
    List<FarmResponse> list() {
        return farms.list();
    }

    @GetMapping("/{id}")
    FarmDetail detail(@PathVariable UUID id) {
        return farms.detail(id);
    }

    @PostMapping
    FarmResponse create(@Valid @RequestBody FarmRequest request) {
        return farms.create(request);
    }

    @PutMapping("/{id}")
    FarmResponse update(@PathVariable UUID id, @Valid @RequestBody FarmRequest request) {
        return farms.update(id, request);
    }
}

