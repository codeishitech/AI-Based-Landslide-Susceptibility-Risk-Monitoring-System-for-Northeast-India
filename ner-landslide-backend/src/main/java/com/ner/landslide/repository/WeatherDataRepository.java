package com.ner.landslide.repository;

import com.ner.landslide.entity.WeatherData;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WeatherDataRepository extends JpaRepository<WeatherData, UUID> {

    Optional<WeatherData> findFirstByRiskZoneIdOrderByObservedAtDesc(UUID riskZoneId);

    List<WeatherData> findByRiskZoneIdAndObservedAtAfterOrderByObservedAtDesc(UUID riskZoneId, Instant after);

    List<WeatherData> findByObservedAtAfter(Instant after);
}
