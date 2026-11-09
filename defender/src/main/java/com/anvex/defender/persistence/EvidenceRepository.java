package com.anvex.defender.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface EvidenceRepository extends JpaRepository<Entities.EvidenceEntity, Long> {

    Optional<Entities.EvidenceEntity> findByDecisionId(String decisionId);
    List<Entities.EvidenceEntity> findTop100ByOrderByCreatedAtDesc();
}
