package com.shambanexa.controller;

import com.shambanexa.dto.MasterDtos.BreedBody;
import com.shambanexa.dto.MasterDtos.CategoryView;
import com.shambanexa.dto.MasterDtos.DetailView;
import com.shambanexa.dto.MasterDtos.ImportPreview;
import com.shambanexa.dto.MasterDtos.PageView;
import com.shambanexa.dto.MasterDtos.RecordView;
import com.shambanexa.dto.MasterDtos.ReferenceBody;
import com.shambanexa.dto.MasterDtos.SpeciesBody;
import com.shambanexa.dto.MasterDtos.StatusBody;
import com.shambanexa.implementation.MasterDataService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/master")
public class MasterDataController {

    private final MasterDataService master;

    public MasterDataController(MasterDataService master) {
        this.master = master;
    }

    @GetMapping
    List<CategoryView> categories() {
        return master.categories();
    }

    @GetMapping("/{category}")
    PageView page(@PathVariable String category,
                  @RequestParam(required = false) String q,
                  @RequestParam(required = false) String status,
                  @RequestParam(required = false) UUID parentId,
                  @RequestParam(defaultValue = "") String sort,
                  @RequestParam(defaultValue = "asc") String direction,
                  @RequestParam(defaultValue = "0") int page,
                  @RequestParam(defaultValue = "8") int size) {
        return master.page(category, q, status, parentId, sort, direction, page, size);
    }

    @GetMapping("/{category}/{id}")
    DetailView detail(@PathVariable String category, @PathVariable UUID id) {
        return master.detail(category, id);
    }

    @PostMapping("/species")
    RecordView createSpecies(@Valid @RequestBody SpeciesBody body) {
        return master.createSpecies(body);
    }

    @PutMapping("/species/{id}")
    RecordView updateSpecies(@PathVariable UUID id, @Valid @RequestBody SpeciesBody body) {
        return master.updateSpecies(id, body);
    }

    @PostMapping("/species/{id}/status")
    RecordView statusSpecies(@PathVariable UUID id, @Valid @RequestBody StatusBody body) {
        return master.statusSpecies(id, body.active());
    }

    @DeleteMapping("/species/{id}")
    void deleteSpecies(@PathVariable UUID id) {
        master.deleteSpecies(id);
    }

    @PostMapping("/breeds")
    RecordView createBreed(@Valid @RequestBody BreedBody body) {
        return master.createBreed(body);
    }

    @PutMapping("/breeds/{id}")
    RecordView updateBreed(@PathVariable UUID id, @Valid @RequestBody BreedBody body) {
        return master.updateBreed(id, body);
    }

    @PostMapping("/breeds/{id}/status")
    RecordView statusBreed(@PathVariable UUID id, @Valid @RequestBody StatusBody body) {
        return master.statusBreed(id, body.active());
    }

    @DeleteMapping("/breeds/{id}")
    void deleteBreed(@PathVariable UUID id) {
        master.deleteBreed(id);
    }

    @PostMapping("/references/{kind}")
    RecordView createReference(@PathVariable String kind, @Valid @RequestBody ReferenceBody body) {
        return master.createReference(kind, body);
    }

    @PutMapping("/references/{id}")
    RecordView updateReference(@PathVariable UUID id, @Valid @RequestBody ReferenceBody body) {
        return master.updateReference(id, body);
    }

    @PostMapping("/references/{id}/status")
    RecordView statusReference(@PathVariable UUID id, @Valid @RequestBody StatusBody body) {
        return master.statusReference(id, body.active());
    }

    @DeleteMapping("/references/{id}")
    void deleteReference(@PathVariable UUID id) {
        master.deleteReference(id);
    }

    @GetMapping("/{category}/export")
    ResponseEntity<byte[]> export(@PathVariable String category, @RequestParam(defaultValue = "csv") String format) {
        boolean excel = "xlsx".equalsIgnoreCase(format);
        byte[] body = master.export(category, excel ? "xlsx" : "csv");
        String filename = category.toLowerCase() + (excel ? ".xlsx" : ".csv");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType(excel
                        ? "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                        : "text/csv"))
                .body(body);
    }

    @PostMapping(value = "/species/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ImportPreview importSpecies(@RequestParam(defaultValue = "false") boolean apply,
                                @RequestParam(defaultValue = "false") boolean updateExisting,
                                @RequestParam("file") MultipartFile file) throws IOException {
        return master.importSpecies(apply, updateExisting, file.getOriginalFilename(), file.getBytes());
    }

    @PostMapping(value = "/breeds/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ImportPreview importBreeds(@RequestParam(defaultValue = "false") boolean apply,
                               @RequestParam(defaultValue = "false") boolean updateExisting,
                               @RequestParam("file") MultipartFile file) throws IOException {
        return master.importBreeds(apply, updateExisting, file.getOriginalFilename(), file.getBytes());
    }
}
