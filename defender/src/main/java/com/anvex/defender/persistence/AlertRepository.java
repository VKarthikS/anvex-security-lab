package com.anvex.defender.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface AlertRepository extends JpaRepository<Entities.AlertEntity, Long> {

    Optional<Entities.AlertEntity> findByAlertId(String alertId);
    List<Entities.AlertEntity> findTop100ByOrderByLastSeenDesc();
    List<Entities.AlertEntity> findBySourceIp(String sourceIp);
}
