package com.shambanexa.controller;
import com.shambanexa.dto.AnimalRequest;
import com.shambanexa.dto.AnimalResponse;
import com.shambanexa.dto.PageResponse;
import com.shambanexa.dto.TransferRequest;
import com.shambanexa.service.AnimalService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/animals")
public class AnimalController {
    private final AnimalService animals;

    AnimalController(AnimalService animals) {
        this.animals = animals;
    }

    @GetMapping
    PageResponse<AnimalResponse> list(@RequestParam(required = false) UUID farmId,
                                      @RequestParam(required = false) UUID speciesId,
                                      @RequestParam(required = false) String q,
                                      @RequestParam(defaultValue = "0") int page,
                                      @RequestParam(defaultValue = "20") int size) {
        return animals.list(farmId, speciesId, q, page, size);
    }

    @GetMapping("/{id}")
    AnimalResponse get(@PathVariable UUID id) {
        return animals.get(id);
    }

    @PostMapping
    AnimalResponse create(@Valid @RequestBody AnimalRequest request) {
        return animals.create(request);
    }

    @PostMapping("/{id}/transfer")
    AnimalResponse transfer(@PathVariable UUID id, @Valid @RequestBody TransferRequest request) {
        return animals.transfer(id, request);
    }
}

