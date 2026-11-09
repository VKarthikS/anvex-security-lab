package com.anvex.defender.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface SourceBlockRepository extends JpaRepository<Entities.SourceBlockEntity, Long> {

    List<Entities.SourceBlockEntity> findByState(String state);
    List<Entities.SourceBlockEntity> findTop100ByOrderByCreatedAtDesc();
    Optional<Entities.SourceBlockEntity> findByBlockId(String blockId);
}
