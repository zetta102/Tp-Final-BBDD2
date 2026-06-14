package com.bbdd.tp.repository;

import com.bbdd.tp.model.ComponentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ComponentRepository extends JpaRepository<ComponentEntity, UUID> {
    List<ComponentEntity> findByTrackId(UUID trackId);
}