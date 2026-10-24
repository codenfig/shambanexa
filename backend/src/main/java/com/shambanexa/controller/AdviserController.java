package com.shambanexa.controller;

import com.shambanexa.dto.AdviserProfileRequest;
import com.shambanexa.dto.AdviserRecommendationRequest;
import com.shambanexa.dto.AdviserRatingRequest;
import com.shambanexa.dto.AdviserResponse;
import com.shambanexa.service.AdviserDirectoryService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/advisers")
public class AdviserController {

    private final AdviserDirectoryService advisers;

    public AdviserController(AdviserDirectoryService advisers) {
        this.advisers = advisers;
    }

    @GetMapping
    List<AdviserResponse> list(
            @RequestParam(required = false) String talent,
            @RequestParam(required = false) String availability,
            @RequestParam(required = false) String load
    ) {
        return advisers.list(talent, availability, load);
    }

    @PutMapping("/me")
    AdviserResponse updateProfile(@RequestBody AdviserProfileRequest request) {
        return advisers.updateProfile(request);
    }

    @PostMapping("/{id}/rating")
    AdviserResponse rate(@PathVariable UUID id, @RequestBody AdviserRatingRequest request) {
        return advisers.rate(id, request);
    }

    @PostMapping("/{id}/recommendation")
    AdviserResponse recommend(@PathVariable UUID id, @RequestBody AdviserRecommendationRequest request) {
        return advisers.recommend(id, request);
    }

    @DeleteMapping("/{id}/recommendation")
    AdviserResponse withdrawRecommendation(@PathVariable UUID id) {
        return advisers.withdrawRecommendation(id);
    }
}
