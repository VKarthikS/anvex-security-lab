package com.anvex.defender.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface SecurityEventRepository extends JpaRepository<Entities.SecurityEventEntity, Long> {

    List<Entities.SecurityEventEntity> findTop50ByOrderByOccurredAtDesc();
}
