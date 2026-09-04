package com.ner.landslide.repository;

import com.ner.landslide.entity.RiskPrediction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PredictionRepository extends JpaRepository<RiskPrediction, UUID> {

    List<RiskPrediction> findByRiskZoneIdOrderByCreatedAtDesc(UUID riskZoneId);

    Optional<RiskPrediction> findFirstByRiskZoneIdOrderByCreatedAtDesc(UUID riskZoneId);

    @Query("""
            SELECT p FROM RiskPrediction p
            WHERE p.createdAt >= :since
            ORDER BY p.createdAt DESC
            """)
    List<RiskPrediction> findSince(@Param("since") Instant since);

    long countByRiskZoneIdAndCreatedAtAfter(UUID riskZoneId, Instant after);
}
