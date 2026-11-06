package com.shambanexa.repository;

import com.shambanexa.entity.HerdBatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface HerdBatchRepository extends JpaRepository<HerdBatch, UUID> {

    List<HerdBatch> findByTenantIdAndStatusOrderByNameAsc(UUID tenantId, String status);

    java.util.Optional<HerdBatch> findByIdAndTenantId(UUID id, UUID tenantId);

    long countByTenantIdAndSpeciesId(UUID tenantId, UUID speciesId);

    long countByTenantIdAndBreedId(UUID tenantId, UUID breedId);

    @Query("""
            select b.farmId, sum(b.quantity)
            from HerdBatch b, Species s
            where s.id = b.speciesId
              and s.category = 'LIVESTOCK'
              and b.tenantId = :tenantId
              and b.status = 'ACTIVE'
              and (:allFarms = true or b.farmId in :farmIds)
            group by b.farmId
            """)
    List<Object[]> sumLivestockByFarm(UUID tenantId, boolean allFarms, Collection<UUID> farmIds);

    @Query("""
            select b.speciesId, sum(b.quantity)
            from HerdBatch b, Species s
            where s.id = b.speciesId
              and s.category = 'LIVESTOCK'
              and b.tenantId = :tenantId
              and b.status = 'ACTIVE'
              and (:allFarms = true or b.farmId in :farmIds)
            group by b.speciesId
            """)
    List<Object[]> sumLivestockBySpecies(UUID tenantId, boolean allFarms, Collection<UUID> farmIds);

    @Query("""
            select coalesce(sum(b.quantity), 0)
            from HerdBatch b, Species s
            where s.id = b.speciesId
              and s.category = 'LIVESTOCK'
              and b.tenantId = :tenantId
              and b.status = 'ACTIVE'
              and (:allFarms = true or b.farmId in :farmIds)
            """)
    long sumActiveLivestock(UUID tenantId, boolean allFarms, Collection<UUID> farmIds);
}
