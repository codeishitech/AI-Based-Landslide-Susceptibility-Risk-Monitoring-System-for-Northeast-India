package com.ner.landslide.dto;

import com.ner.landslide.entity.enums.RiskLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PredictionResponse {

    private UUID id;
    private String zoneCode;
    private Double riskScore;
    private Double confidence;
    private Double compositeScore;
    private RiskLevel riskLevel;
    private String predictionWindow;
    private String modelVersion;
    private boolean alertGenerated;
    private Instant createdAt;
}
