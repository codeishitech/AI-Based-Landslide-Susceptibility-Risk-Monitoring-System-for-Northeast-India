package com.ner.landslide.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Map;

/**
 * Response payload returned by the Python FastAPI ML microservice GET /health endpoint.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MlHealthResponse {

    private String status;

    @JsonProperty("model_loaded")
    private Boolean modelLoaded;

    @JsonProperty("rainfall_coverage")
    private Double rainfallCoverage;

    @JsonProperty("rainfall_data_status")
    private String rainfallDataStatus;

    private Map<String, Object> details;
}
