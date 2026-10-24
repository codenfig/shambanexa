package com.shambanexa.controller;

import com.shambanexa.dto.AdviceRequest;
import com.shambanexa.dto.AdviceResponse;
import com.shambanexa.service.AdviceService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/advice")
public class AdviceController {

    private final AdviceService advice;

    public AdviceController(AdviceService advice) {
        this.advice = advice;
    }

    @GetMapping
    List<AdviceResponse> list() {
        return advice.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    AdviceResponse publish(@Valid @RequestBody AdviceRequest request) {
        return advice.publish(request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void remove(@PathVariable UUID id) {
        advice.remove(id);
    }
}
