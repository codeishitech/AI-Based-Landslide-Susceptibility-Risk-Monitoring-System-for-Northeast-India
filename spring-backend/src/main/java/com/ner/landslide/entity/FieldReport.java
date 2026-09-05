package com.ner.landslide.entity;

import com.ner.landslide.entity.enums.ReportSeverity;
import com.ner.landslide.entity.enums.ReportStatus;
import com.ner.landslide.entity.enums.ReportType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.locationtech.jts.geom.Point;

import java.time.Instant;
import java.util.UUID;

/** A citizen or field-officer submitted report of a hazard/incident. */
@Entity
@Table(name = "field_reports", indexes = {
        @Index(name = "idx_reports_status", columnList = "status"),
        @Index(name = "idx_reports_zone", columnList = "risk_zone_id"),
        @Index(name = "idx_reports_client_id", columnList = "client_generated_id", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FieldReport {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * Client-generated idempotency key used for offline synchronization
     * (POST /api/v1/reports/sync). Prevents duplicate inserts when a device
     * retries a sync after a dropped connection.
     */
    @Column(name = "client_generated_id", length = 100, unique = true)
    private String clientGeneratedId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reported_by")
    private Profile reportedBy;

    /** Nearest risk zone, resolved via spatial lookup at ingestion time; may be null. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "risk_zone_id")
    private RiskZone riskZone;

    @Enumerated(EnumType.STRING)
    @Column(name = "report_type", nullable = false, length = 30)
    private ReportType reportType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReportSeverity severity;

    @Column(columnDefinition = "text")
    private String description;

    @JdbcTypeCode(SqlTypes.GEOMETRY)
    @Column(name = "location", columnDefinition = "geometry(Point,4326)", nullable = false)
    private Point location;

    @Column(name = "media_url", length = 500)
    private String mediaUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private ReportStatus status = ReportStatus.PENDING;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
