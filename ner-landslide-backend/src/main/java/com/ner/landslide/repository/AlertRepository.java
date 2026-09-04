package com.ner.landslide.repository;

import com.ner.landslide.entity.Alert;
import com.ner.landslide.entity.enums.AlertStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface AlertRepository extends JpaRepository<Alert, UUID> {

    List<Alert> findByStatusOrderByCreatedAtDesc(AlertStatus status);

    List<Alert> findByRiskZoneIdAndStatus(UUID riskZoneId, AlertStatus status);

    long countByStatus(AlertStatus status);

    @Query("""
            SELECT a FROM Alert a
            WHERE a.status = 'ACTIVE' AND a.expiresAt IS NOT NULL AND a.expiresAt < :now
            """)
    List<Alert> findExpiredButStillActive(@Param("now") Instant now);

    /** Prevents duplicate CRITICAL/HIGH alert spam for the same zone within a short window. */
    boolean existsByRiskZoneIdAndStatusAndCreatedAtAfter(UUID riskZoneId, AlertStatus status, Instant after);
}
