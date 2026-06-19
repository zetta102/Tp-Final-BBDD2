package com.bbdd.tp.repository;

import com.bbdd.tp.model.ComponentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link ComponentEntity} instances stored in PostgreSQL.
 *
 * <p>Provides CRUD operations on the {@code components} catalog table and
 * a derived query for retrieving all components belonging to a given track.</p>
 */
@Repository
public interface ComponentRepository extends JpaRepository<ComponentEntity, UUID> {

    /**
     * Finds all components associated with the specified track.
     *
     * @param trackId the track identifier to filter by
     * @return list of components belonging to the track, possibly empty
     */
    List<ComponentEntity> findByTrackId(UUID trackId);
}