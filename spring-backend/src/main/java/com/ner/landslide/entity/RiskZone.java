package com.ner.landslide.entity;

import com.ner.landslide.entity.enums.RiskLevel;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;

import java.time.Instant;
import java.util.UUID;

/**
 * A geographically defined landslide-prone region.
 *
 * `centroid` is a Point used for fast radius/"nearby" queries; `geometry`
 * is the full polygon boundary of the zone (used for containment queries,
 * e.g. "find villages inside this zone" or "roads intersecting this zone").
 */
@Entity
@Table(name = "risk_zones", indexes = {
        @Index(name = "idx_risk_zones_state_district", columnList = "state,district"),
        @Index(name = "idx_risk_zones_risk_level", columnList = "current_risk_level")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RiskZone {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Human-friendly external code, e.g. "NER-102", referenced by the ML service. */
    @Column(name = "code", nullable = false, unique = true, length = 40)
    private String code;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false, length = 60)
    private String state;

    @Column(nullable = false, length = 100)
    private String district;

    @JdbcTypeCode(SqlTypes.GEOMETRY)
    @Column(name = "centroid", columnDefinition = "geometry(Point,4326)", nullable = false)
    private Point centroid;

    @JdbcTypeCode(SqlTypes.GEOMETRY)
    @Column(name = "geometry", columnDefinition = "geometry(Polygon,4326)")
    private Polygon geometry;

    @Column(name = "current_risk_score")
    private Double currentRiskScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "current_risk_level", length = 20)
    @Builder.Default
    private RiskLevel currentRiskLevel = RiskLevel.LOW;

    @Column(name = "nearby_villages", columnDefinition = "text")
    private String nearbyVillages;

    @Column(name = "affected_roads", columnDefinition = "text")
    private String affectedRoads;

    @Column(name = "infrastructure_notes", columnDefinition = "text")
    private String infrastructureNotes;

    @Column(name = "population_exposure")
    private Integer populationExposure;

    @Column(name = "infrastructure_criticality")
    @Builder.Default
    private Integer infrastructureCriticality = 1;

    @Column(name = "last_updated", nullable = false)
    @Builder.Default
    private Instant lastUpdated = Instant.now();

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @PreUpdate
    public void onUpdate() {
        this.lastUpdated = Instant.now();
    }
}
