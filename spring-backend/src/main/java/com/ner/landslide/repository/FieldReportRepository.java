package com.ner.landslide.repository;

import com.ner.landslide.entity.FieldReport;
import com.ner.landslide.entity.enums.ReportStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FieldReportRepository extends JpaRepository<FieldReport, UUID> {

    Optional<FieldReport> findByClientGeneratedId(String clientGeneratedId);

    List<FieldReport> findByStatus(ReportStatus status);

    List<FieldReport> findByRiskZoneId(UUID riskZoneId);

    /** Reports created or updated since a given timestamp, used by the offline sync endpoint. */
    @Query("""
            SELECT r FROM FieldReport r
            WHERE r.createdAt >= :since OR r.updatedAt >= :since
            ORDER BY r.createdAt ASC
            """)
    List<FieldReport> findChangesSince(@Param("since") Instant since);

    long countByReportTypeAndCreatedAtAfter(com.ner.landslide.entity.enums.ReportType type, Instant after);

    @Query(value = """
            SELECT * FROM field_reports r
            WHERE ST_DWithin(
                r.location,
                ST_SetSRID(ST_MakePoint(:lng, :lat), 4326),
                :radiusMeters / 111000.0
            )
            ORDER BY r.created_at DESC
            """, nativeQuery = true)
    List<FieldReport> findNearby(@Param("lat") double lat,
                                  @Param("lng") double lng,
                                  @Param("radiusMeters") double radiusMeters);
}
