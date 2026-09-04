package com.ner.landslide.dto;

import com.ner.landslide.entity.enums.RiskLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** API response shape for a RiskZone, exposing coordinates instead of raw JTS geometry. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RiskZoneResponse {

    private UUID id;
    private String code;
    private String name;
    private String state;
    private String district;
    private double latitude;
    private double longitude;
    private Double currentRiskScore;
    private RiskLevel currentRiskLevel;
    private Integer populationExposure;
    private Integer infrastructureCriticality;
    private String nearbyVillages;
    private String affectedRoads;
    private Instant lastUpdated;

    /** Populated only for spatial "nearby" queries. */
    private Double distanceKm;
}
