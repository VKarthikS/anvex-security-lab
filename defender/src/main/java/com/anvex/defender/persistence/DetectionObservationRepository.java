package com.anvex.defender.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface DetectionObservationRepository extends JpaRepository<Entities.DetectionObservationEntity, Long> {

    List<Entities.DetectionObservationEntity> findByOccurredAtAfter(Instant occurredAt);
}
