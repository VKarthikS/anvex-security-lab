package com.anvex.defender.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface AnalystActionRepository extends JpaRepository<Entities.AnalystActionEntity, Long> {

    List<Entities.AnalystActionEntity> findTop50ByOrderByCreatedAtDesc();
}
