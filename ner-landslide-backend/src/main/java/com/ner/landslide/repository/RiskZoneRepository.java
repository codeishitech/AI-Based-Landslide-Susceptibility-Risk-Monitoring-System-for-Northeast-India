package com.ner.landslide.repository;

import com.ner.landslide.entity.RiskZone;
import com.ner.landslide.entity.enums.RiskLevel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RiskZoneRepository extends JpaRepository<RiskZone, UUID> {

    Optional<RiskZone> findByCode(String code);

    List<RiskZone> findByCurrentRiskLevelIn(List<RiskLevel> levels);

    List<RiskZone> findByStateIgnoreCaseAndDistrictIgnoreCase(String state, String district);

    /**
     * Spatial "nearby" search: returns risk zones whose centroid lies within
     * {@code radiusKm} kilometers of the given point, nearest first.
     * Uses PostGIS ST_DWithin (index-friendly, uses geography cast for accurate
     * metric distance) combined with ST_Distance for ordering.
     */
    @Query(value = """
            SELECT * FROM risk_zones z
            WHERE ST_DWithin(
                z.centroid,
                ST_SetSRID(ST_MakePoint(:lng, :lat), 4326),
                :radiusMeters / 111000.0
            )
            ORDER BY ST_Distance(
                z.centroid,
                ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)
            ) ASC
            """, nativeQuery = true)
    List<RiskZone> findNearby(@Param("lat") double lat,
                               @Param("lng") double lng,
                               @Param("radiusMeters") double radiusMeters);

    /**
     * Finds the single nearest risk zone to a point, regardless of distance.
     * Used by FieldReportService to attach an incoming report to the closest
     * zone even when it falls slightly outside any zone's defined radius.
     */
    @Query(value = """
            SELECT * FROM risk_zones z
            ORDER BY ST_Distance(
                z.centroid,
                ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)
            ) ASC
            LIMIT 1
            """, nativeQuery = true)
    Optional<RiskZone> findNearest(@Param("lat") double lat, @Param("lng") double lng);

    /** Zones whose polygon boundary contains the given point. */
    @Query(value = """
            SELECT * FROM risk_zones z
            WHERE z.geometry IS NOT NULL
              AND ST_Contains(z.geometry, ST_SetSRID(ST_MakePoint(:lng, :lat), 4326))
            """, nativeQuery = true)
    List<RiskZone> findContainingPoint(@Param("lat") double lat, @Param("lng") double lng);

    long countByCurrentRiskLevel(RiskLevel level);
}
