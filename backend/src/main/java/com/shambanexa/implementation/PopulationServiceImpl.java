package com.shambanexa.implementation;

import com.shambanexa.service.PopulationService;

import com.shambanexa.repository.AnimalRepository;
import com.shambanexa.repository.HerdBatchRepository;
import com.shambanexa.repository.MovementEventRepository;
import com.shambanexa.security.AccessGuard;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class PopulationServiceImpl implements PopulationService {

    private final AnimalRepository animals;
    private final HerdBatchRepository batches;
    private final MovementEventRepository movements;
    private final AccessGuard access;

    public PopulationServiceImpl(AnimalRepository animals, HerdBatchRepository batches,
                             MovementEventRepository movements, AccessGuard access) {
        this.animals = animals;
        this.batches = batches;
        this.movements = movements;
        this.access = access;
    }

    public long total() {
        return animals.countActiveLivestock(tenant(), access.allFarms(), scope())
                + batches.sumActiveLivestock(tenant(), access.allFarms(), scope());
    }

    public Map<UUID, Long> byFarm() {
        Map<UUID, Long> totals = merge(
                batches.sumLivestockByFarm(tenant(), access.allFarms(), scope()),
                animals.countLivestockByFarm(tenant(), access.allFarms(), scope())
        );
        return totals;
    }

    public Map<UUID, Long> bySpecies() {
        return merge(
                batches.sumLivestockBySpecies(tenant(), access.allFarms(), scope()),
                animals.countLivestockBySpecies(tenant(), access.allFarms(), scope())
        );
    }

    public boolean reconciled() {
        long projection = total();
        long ledger = movements.sumLedger(tenant(), access.allFarms(), scope());
        return projection == ledger;
    }

    private Map<UUID, Long> merge(List<Object[]> left, List<Object[]> right) {
        Map<UUID, Long> totals = new HashMap<>();
        add(totals, left);
        add(totals, right);
        return totals;
    }

    private void add(Map<UUID, Long> totals, List<Object[]> rows) {
        for (Object[] row : rows) {
            UUID id = (UUID) row[0];
            long value = ((Number) row[1]).longValue();
            totals.merge(id, value, Long::sum);
        }
    }

    private UUID tenant() {
        return access.tenantId();
    }

    private Collection<UUID> scope() {
        return access.farmScope();
    }
}
