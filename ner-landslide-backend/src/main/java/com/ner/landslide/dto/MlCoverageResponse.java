package com.ner.landslide.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Response payload returned by the Python FastAPI ML microservice GET /coverage endpoint.
 * Contains DEM tile coverage bounding boxes so frontends can grey out areas with no coverage.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MlCoverageResponse {

    @JsonProperty("covered_tiles_count")
    private Integer coveredTilesCount;

    @JsonProperty("coverage_percentage")
    private Double coveragePercentage;

    @JsonProperty("bounding_boxes")
    private List<Object> boundingBoxes;
}
