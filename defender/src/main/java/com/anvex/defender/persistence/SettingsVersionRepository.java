package com.anvex.defender.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface SettingsVersionRepository extends JpaRepository<Entities.SettingsVersionEntity, Long> {

    Optional<Entities.SettingsVersionEntity> findTopByOrderByCreatedAtDesc();
}
