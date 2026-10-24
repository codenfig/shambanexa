package com.shambanexa.repository;

import com.shambanexa.entity.Animal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AnimalRepository extends JpaRepository<Animal, UUID> {

    Optional<Animal> findByIdAndTenantId(UUID id, UUID tenantId);

    boolean existsByTenantIdAndTagIgnoreCase(UUID tenantId, String tag);

    @Query("""
            select a from Animal a
            where a.tenantId = :tenantId
              and (:allFarms = true or a.farmId in :farmIds)
              and (:farmId is null or a.farmId = :farmId)
              and (:speciesId is null or a.speciesId = :speciesId)
              and (:q = '' or lower(a.tag) like lower(concat('%', :q, '%'))
                   or lower(coalesce(a.name, '')) like lower(concat('%', :q, '%')))
            """)
    Page<Animal> search(UUID tenantId, boolean allFarms, Collection<UUID> farmIds,
                        UUID farmId, UUID speciesId, String q, Pageable pageable);

    @Query("""
            select a.farmId, count(a)
            from Animal a, Species s
            where s.id = a.speciesId
              and s.category = 'LIVESTOCK'
              and a.tenantId = :tenantId
              and a.status = 'ACTIVE'
              and (:allFarms = true or a.farmId in :farmIds)
            group by a.farmId
            """)
    List<Object[]> countLivestockByFarm(UUID tenantId, boolean allFarms, Collection<UUID> farmIds);

    @Query("""
            select a.speciesId, count(a)
            from Animal a, Species s
            where s.id = a.speciesId
              and s.category = 'LIVESTOCK'
              and a.tenantId = :tenantId
              and a.status = 'ACTIVE'
              and (:allFarms = true or a.farmId in :farmIds)
            group by a.speciesId
            """)
    List<Object[]> countLivestockBySpecies(UUID tenantId, boolean allFarms, Collection<UUID> farmIds);

    @Query("""
            select count(a) from Animal a, Species s
            where s.id = a.speciesId
              and s.category = 'LIVESTOCK'
              and a.tenantId = :tenantId
              and a.status = 'ACTIVE'
              and (:allFarms = true or a.farmId in :farmIds)
            """)
    long countActiveLivestock(UUID tenantId, boolean allFarms, Collection<UUID> farmIds);
}
