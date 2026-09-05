package com.ner.landslide.entity;

import com.ner.landslide.entity.enums.RiskLevel;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** A single ML risk prediction received from the AI/ML risk engine for a RiskZone. */
@Entity
@Table(name = "risk_predictions", indexes = {
        @Index(name = "idx_predictions_zone_created", columnList = "risk_zone_id,created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RiskPrediction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "risk_zone_id", nullable = false)
    private RiskZone riskZone;

    @Column(name = "risk_score", nullable = false)
    private Double riskScore;

    @Column(name = "confidence", nullable = false)
    private Double confidence;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level", nullable = false, length = 20)
    private RiskLevel riskLevel;

    @Column(name = "prediction_window", length = 40)
    private String predictionWindow;

    @Column(name = "model_version", length = 40)
    private String modelVersion;

    /** Composite score after RiskEvaluationService blends in rainfall, confidence, etc. */
    @Column(name = "composite_score")
    private Double compositeScore;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
