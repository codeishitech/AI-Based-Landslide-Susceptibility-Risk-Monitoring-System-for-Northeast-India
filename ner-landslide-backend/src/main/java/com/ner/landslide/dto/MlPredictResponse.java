package com.ner.landslide.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Response payload returned by the Python FastAPI ML microservice POST /predict endpoint.
 * Corresponds to: {"risk_probability": float, "risk_level": string, "top_factors": list, "rainfall_data_used": bool/obj}
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MlPredictResponse {

    @JsonProperty("risk_probability")
    private Double riskProbability;

    @JsonProperty("risk_level")
    private String riskLevel;

    @JsonProperty("top_factors")
    private List<String> topFactors;

    @JsonProperty("rainfall_data_used")
    private Object rainfallDataUsed;

    /** Compatibility helper: returns riskProbability as riskScore. */
    public Double getRiskScore() {
        return riskProbability != null ? riskProbability : 0.0;
    }

    /** Compatibility helper: returns default confidence (1.0 or derived) if not explicitly supplied. */
    public Double getConfidence() {
        return 1.0;
    }
}
