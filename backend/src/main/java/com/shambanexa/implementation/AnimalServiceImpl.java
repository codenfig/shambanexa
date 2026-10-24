package com.shambanexa.implementation;

import com.shambanexa.service.AnimalService;
import com.shambanexa.service.AuditTrail;
import com.shambanexa.service.FarmService;

import com.shambanexa.dto.AnimalRequest;
import com.shambanexa.dto.AnimalResponse;
import com.shambanexa.dto.BatchQuantityRequest;
import com.shambanexa.dto.BatchResponse;
import com.shambanexa.dto.PageResponse;
import com.shambanexa.dto.TransferRequest;
import com.shambanexa.common.ApiException;
import com.shambanexa.entity.Animal;
import com.shambanexa.entity.Breed;
import com.shambanexa.entity.Farm;
import com.shambanexa.entity.HerdBatch;
import com.shambanexa.entity.MovementEvent;
import com.shambanexa.entity.Species;
import com.shambanexa.repository.AnimalRepository;
import com.shambanexa.repository.BreedRepository;
import com.shambanexa.repository.HerdBatchRepository;
import com.shambanexa.repository.MovementEventRepository;
import com.shambanexa.repository.SpeciesRepository;
import com.shambanexa.security.AccessGuard;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AnimalServiceImpl implements AnimalService {

    private static final Set<String> SEXES = Set.of("FEMALE", "MALE", "UNKNOWN");
    private static final Set<String> HEALTH = Set.of("HEALTHY", "WATCH", "SICK", "QUARANTINE");

    private final AnimalRepository animals;
    private final HerdBatchRepository batches;
    private final SpeciesRepository species;
    private final BreedRepository breeds;
    private final MovementEventRepository movements;
    private final FarmService farms;
    private final AccessGuard access;
    private final AuditTrail audit;

    public AnimalServiceImpl(AnimalRepository animals, HerdBatchRepository batches, SpeciesRepository species,
                         BreedRepository breeds, MovementEventRepository movements, FarmService farms,
                         AccessGuard access, AuditTrail audit) {
        this.animals = animals;
        this.batches = batches;
        this.species = species;
        this.breeds = breeds;
        this.movements = movements;
        this.farms = farms;
        this.access = access;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public PageResponse<AnimalResponse> list(UUID farmId, UUID speciesId, String q, int page, int size) {
        if (farmId != null) {
            access.checkFarm(farmId);
        }
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        String query = q == null || q.isBlank() ? "" : q.trim();
        Page<Animal> result = animals.search(
                access.tenantId(),
                access.allFarms(),
                access.farmScope(),
                farmId,
                speciesId,
                query,
                PageRequest.of(safePage, safeSize, Sort.by("tag"))
        );
        Map<UUID, Farm> farmMap = farms.visibleFarms().stream()
                .collect(Collectors.toMap(Farm::getId, Function.identity()));
        Map<UUID, Species> speciesMap = species.findByTenantIdOrderBySortOrderAsc(access.tenantId()).stream()
                .collect(Collectors.toMap(Species::getId, Function.identity()));
        Map<UUID, Breed> breedMap = breeds.findAll().stream()
                .filter(breed -> access.tenantId().equals(breed.getTenantId()))
                .collect(Collectors.toMap(Breed::getId, Function.identity()));
        List<AnimalResponse> items = result.getContent().stream()
                .map(animal -> toResponse(animal, farmMap, speciesMap, breedMap))
                .toList();
        return new PageResponse<>(items, result.getTotalElements(), safePage, safeSize);
    }

    @Transactional(readOnly = true)
    public List<BatchResponse> batches() {
        Map<UUID, Farm> farmMap = farms.visibleFarms().stream()
                .collect(Collectors.toMap(Farm::getId, Function.identity()));
        Map<UUID, Species> speciesMap = species.findByTenantIdOrderBySortOrderAsc(access.tenantId()).stream()
                .collect(Collectors.toMap(Species::getId, Function.identity()));
        return batches.findByTenantIdAndStatusOrderByNameAsc(access.tenantId(), "ACTIVE").stream()
                .filter(batch -> access.canReadFarm(batch.getFarmId()))
                .map(batch -> new BatchResponse(
                        batch.getId(),
                        batch.getCode(),
                        batch.getName(),
                        batch.getQuantity(),
                        batch.getPurpose(),
                        batch.getStatus(),
                        batch.getFarmId(),
                        nameOf(farmMap.get(batch.getFarmId())),
                        speciesMap.containsKey(batch.getSpeciesId()) ? speciesMap.get(batch.getSpeciesId()).getName() : ""
                ))
                .toList();
    }

    @Transactional
    public BatchResponse updateQuantity(UUID id, BatchQuantityRequest request) {
        access.requireAuthority("ANIMAL_WRITE");
        HerdBatch batch = batches.findByIdAndTenantId(id, access.tenantId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "BATCH_NOT_FOUND", "Herd batch not found"));
        access.checkFarm(batch.getFarmId());
        int previous = batch.getQuantity();
        batch.setQuantity(request.quantity());
        batches.saveAndFlush(batch);
        audit.record("LIVESTOCK_COUNT_UPDATED", "BATCH", batch.getId(),
                batch.getName() + " headcount " + previous + " to " + request.quantity());
        audit.activity("LIVESTOCK", "Herd count updated", batch.getName() + " is now " + request.quantity(), batch.getFarmId());
        String speciesName = species.findByIdAndTenantId(batch.getSpeciesId(), access.tenantId()).map(Species::getName).orElse("");
        return new BatchResponse(
                batch.getId(),
                batch.getCode(),
                batch.getName(),
                batch.getQuantity(),
                batch.getPurpose(),
                batch.getStatus(),
                batch.getFarmId(),
                farms.load(batch.getFarmId()).getName(),
                speciesName
        );
    }

    @Transactional
    public AnimalResponse create(AnimalRequest request) {
        access.requireAuthority("ANIMAL_WRITE");
        access.checkFarm(request.farmId());
        String key = request.idempotencyKey() == null || request.idempotencyKey().isBlank()
                ? UUID.randomUUID().toString()
                : request.idempotencyKey().trim();
        var existing = movements.findByTenantIdAndIdempotencyKey(access.tenantId(), key);
        if (existing.isPresent()) {
            return get(existing.get().getSubjectId());
        }
        String tag = request.tag().trim().toUpperCase();
        if (animals.existsByTenantIdAndTagIgnoreCase(access.tenantId(), tag)) {
            throw new ApiException(HttpStatus.CONFLICT, "DUPLICATE_TAG", "That tag is already in use");
        }
        Species animalSpecies = species.findByIdAndTenantId(request.speciesId(), access.tenantId())
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "SPECIES_NOT_FOUND", "Choose a species"));
        Breed breed = null;
        if (request.breedId() != null) {
            breed = breeds.findByIdAndTenantId(request.breedId(), access.tenantId())
                    .filter(candidate -> candidate.getSpeciesId().equals(animalSpecies.getId()))
                    .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "BREED_NOT_FOUND", "That breed does not match the species"));
        }
        String sex = request.sex().trim().toUpperCase();
        if (!SEXES.contains(sex)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_SEX", "Sex must be FEMALE, MALE, or UNKNOWN");
        }
        String health = request.healthStatus() == null || request.healthStatus().isBlank()
                ? "HEALTHY"
                : request.healthStatus().trim().toUpperCase();
        if (!HEALTH.contains(health)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_HEALTH", "Health status is not recognized");
        }

        Animal animal = new Animal();
        animal.setTenantId(access.tenantId());
        animal.setFarmId(request.farmId());
        animal.setSpeciesId(animalSpecies.getId());
        animal.setBreedId(breed == null ? null : breed.getId());
        animal.setTag(tag);
        animal.setName(blankToNull(request.name()));
        animal.setSex(sex);
        animal.setBirthDate(request.birthDate());
        animal.setColor(blankToNull(request.color()));
        animal.setStatus("ACTIVE");
        animal.setHealthStatus(health);
        animals.save(animal);

        MovementEvent event = new MovementEvent();
        event.setTenantId(access.tenantId());
        event.setEventType("REGISTRATION");
        event.setSubjectType("ANIMAL");
        event.setSubjectId(animal.getId());
        event.setFarmId(animal.getFarmId());
        event.setQuantity(1);
        event.setActorId(access.current().id());
        event.setReason("Individual registration");
        event.setIdempotencyKey(key);
        movements.save(event);

        audit.record("ANIMAL_REGISTERED", "ANIMAL", animal.getId(), animal.getTag());
        audit.activity("LIVESTOCK", "Animal registered", animal.getTag(), animal.getFarmId());
        return get(animal.getId());
    }

    @Transactional
    public AnimalResponse transfer(UUID id, TransferRequest request) {
        access.requireAuthority("ANIMAL_WRITE");
        String outKey = request.idempotencyKey().trim() + "-out";
        if (movements.existsByTenantIdAndIdempotencyKey(access.tenantId(), outKey)) {
            return get(id);
        }
        Animal animal = animals.findByIdAndTenantId(id, access.tenantId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ANIMAL_NOT_FOUND", "Animal not found"));
        access.checkFarm(animal.getFarmId());
        access.checkFarm(request.destinationFarmId());
        if (!"ACTIVE".equals(animal.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "ANIMAL_NOT_ACTIVE", "Only an active animal can be transferred");
        }
        if (animal.getFarmId().equals(request.destinationFarmId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SAME_FARM", "Choose a different destination farm");
        }
        UUID source = animal.getFarmId();
        animal.setFarmId(request.destinationFarmId());
        animals.save(animal);

        postTransfer(animal.getId(), source, request.destinationFarmId(), -1, outKey, request.reason());
        postTransfer(animal.getId(), request.destinationFarmId(), source, 1, request.idempotencyKey().trim() + "-in", request.reason());
        audit.record("ANIMAL_TRANSFERRED", "ANIMAL", animal.getId(), request.reason().trim());
        audit.activity("LIVESTOCK", "Animal transferred", animal.getTag(), request.destinationFarmId());
        return get(animal.getId());
    }

    @Transactional(readOnly = true)
    public AnimalResponse get(UUID id) {
        Animal animal = animals.findByIdAndTenantId(id, access.tenantId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ANIMAL_NOT_FOUND", "Animal not found"));
        access.checkFarm(animal.getFarmId());
        Farm farm = farms.load(animal.getFarmId());
        Species animalSpecies = species.findById(animal.getSpeciesId()).orElse(null);
        Breed breed = animal.getBreedId() == null ? null : breeds.findById(animal.getBreedId()).orElse(null);
        return toResponse(animal, Map.of(farm.getId(), farm),
                animalSpecies == null ? Map.of() : Map.of(animalSpecies.getId(), animalSpecies),
                breed == null ? Map.of() : Map.of(breed.getId(), breed));
    }

    private void postTransfer(UUID animalId, UUID farmId, UUID otherFarmId, int quantity, String key, String reason) {
        MovementEvent event = new MovementEvent();
        event.setTenantId(access.tenantId());
        event.setEventType(quantity < 0 ? "TRANSFER_OUT" : "TRANSFER_IN");
        event.setSubjectType("ANIMAL");
        event.setSubjectId(animalId);
        event.setFarmId(farmId);
        event.setSourceFarmId(quantity < 0 ? farmId : otherFarmId);
        event.setDestinationFarmId(quantity < 0 ? otherFarmId : farmId);
        event.setQuantity(quantity);
        event.setActorId(access.current().id());
        event.setReason(reason.trim());
        event.setIdempotencyKey(key);
        movements.save(event);
    }

    private AnimalResponse toResponse(Animal animal, Map<UUID, Farm> farmsById,
                                      Map<UUID, Species> speciesById, Map<UUID, Breed> breedsById) {
        Species animalSpecies = speciesById.get(animal.getSpeciesId());
        Breed breed = animal.getBreedId() == null ? null : breedsById.get(animal.getBreedId());
        Farm farm = farmsById.get(animal.getFarmId());
        return new AnimalResponse(
                animal.getId(),
                animal.getTag(),
                animal.getName(),
                animal.getSex(),
                animal.getStatus(),
                animal.getHealthStatus(),
                animal.getBirthDate(),
                animal.getColor(),
                animal.getFarmId(),
                farm == null ? "" : farm.getName(),
                animal.getSpeciesId(),
                animalSpecies == null ? "" : animalSpecies.getName(),
                animal.getBreedId(),
                breed == null ? null : breed.getName(),
                animal.getVersion()
        );
    }

    private String nameOf(Farm farm) {
        return farm == null ? "" : farm.getName();
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
