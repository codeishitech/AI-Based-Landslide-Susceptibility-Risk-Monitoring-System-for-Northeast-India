package com.ner.landslide.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Payload the AI/ML risk engine posts to {@code POST /api/v1/predictions}.
 * Mirrors the ML Output contract described in the platform README.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PredictionRequest {

    @NotBlank(message = "zoneId is required")
    private String zoneId;

    @NotNull(message = "riskScore is required")
    @DecimalMin(value = "0.0", message = "riskScore must be >= 0")
    @DecimalMax(value = "1.0", message = "riskScore must be <= 1")
    private Double riskScore;

    @NotNull(message = "confidence is required")
    @DecimalMin(value = "0.0", message = "confidence must be >= 0")
    @DecimalMax(value = "1.0", message = "confidence must be <= 1")
    private Double confidence;

    /**
     * The raw riskLevel classification claimed by the ML service. Spring Boot
     * independently re-derives risk level via RiskLevel.fromScore() and does
     * not blindly trust this field for business decisions — see
     * RiskEvaluationService for the authoritative evaluation.
     */
    private String riskLevel;

    private String predictionWindow;

    private String modelVersion;
}
