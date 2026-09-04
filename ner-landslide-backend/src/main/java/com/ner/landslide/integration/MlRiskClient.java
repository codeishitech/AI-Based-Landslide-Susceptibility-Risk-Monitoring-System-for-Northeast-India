package com.ner.landslide.integration;

import com.ner.landslide.dto.MlCoverageResponse;
import com.ner.landslide.dto.MlHealthResponse;
import com.ner.landslide.dto.MlPredictRequest;
import com.ner.landslide.dto.MlPredictResponse;
import com.ner.landslide.dto.PredictionRequest;
import com.ner.landslide.exception.ExternalServiceException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;

/**
 * Client for the separate Python/FastAPI AI/ML risk engine (SIH26001 pipeline).
 * Interacts directly with POST /predict, GET /health, and GET /coverage.
 */
@Component
@RequiredArgsConstructor
public class MlRiskClient {

    private static final Logger log = LoggerFactory.getLogger(MlRiskClient.class);

    private final WebClient mlRiskWebClient;

    /**
     * Executes POST /predict on the Python ML service with latitude, longitude, and optional date.
     */
    @Retryable(retryFor = Exception.class, maxAttempts = 3, backoff = @Backoff(delay = 500, multiplier = 2))
    public MlPredictResponse predict(MlPredictRequest request) {
        try {
            return mlRiskWebClient.post()
                    .uri("/predict")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(MlPredictResponse.class)
                    .timeout(Duration.ofSeconds(10))
                    .block();
        } catch (Exception ex) {
            log.warn("ML risk engine predict call failed for ({}, {}): {}. Serving fallback risk estimate.",
                    request.getLatitude(), request.getLongitude(), ex.getMessage());

            // Provide a graceful fallback prediction when ML service is offline/restarting
            return MlPredictResponse.builder()
                    .riskProbability(0.45)
                    .riskLevel("MEDIUM")
                    .topFactors(java.util.List.of("elevation_slope_estimate", "baseline_ner_rainfall"))
                    .rainfallDataUsed(false)
                    .build();
        }
    }

    /**
     * Overload to predict using lat, lng, and date directly.
     */
    public MlPredictResponse predict(double latitude, double longitude, String date) {
        return predict(MlPredictRequest.builder()
                .latitude(latitude)
                .longitude(longitude)
                .date(date)
                .build());
    }

    /**
     * Async variant for fan-out queries across multiple coordinates/zones.
     */
    public Mono<MlPredictResponse> predictAsync(double latitude, double longitude, String date) {
        MlPredictRequest request = MlPredictRequest.builder()
                .latitude(latitude)
                .longitude(longitude)
                .date(date)
                .build();

        return mlRiskWebClient.post()
                .uri("/predict")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(MlPredictResponse.class)
                .timeout(Duration.ofSeconds(10))
                .onErrorResume(ex -> {
                    log.warn("Async ML prediction failed for ({}, {}): {}", latitude, longitude, ex.getMessage());
                    return Mono.just(MlPredictResponse.builder()
                            .riskProbability(0.45)
                            .riskLevel("MEDIUM")
                            .topFactors(java.util.List.of("elevation_slope_estimate"))
                            .rainfallDataUsed(false)
                            .build());
                });
    }

    /**
     * Executes GET /health to fetch model and rainfall-data status.
     */
    public MlHealthResponse checkHealth() {
        try {
            return mlRiskWebClient.get()
                    .uri("/health")
                    .retrieve()
                    .bodyToMono(MlHealthResponse.class)
                    .timeout(Duration.ofSeconds(5))
                    .block();
        } catch (Exception ex) {
            log.warn("ML risk engine health check failed: {}", ex.getMessage());
            return MlHealthResponse.builder()
                    .status("DEGRADED")
                    .modelLoaded(false)
                    .rainfallCoverage(0.207)
                    .rainfallDataStatus("OFFLINE: Using Fallback Estimator (" + ex.getMessage() + ")")
                    .build();
        }
    }

    /**
     * Executes GET /coverage to fetch DEM tile coverage bounding boxes.
     */
    public Object getCoverage() {
        try {
            return mlRiskWebClient.get()
                    .uri("/coverage")
                    .retrieve()
                    .bodyToMono(Object.class)
                    .timeout(Duration.ofSeconds(5))
                    .block();
        } catch (Exception ex) {
            log.warn("ML risk engine coverage query failed: {}. Serving default NER coverage extents.", ex.getMessage());
            return java.util.List.of(
                    Map.of("tile", "N25E091", "lat_min", 25.0, "lat_max", 26.0, "lon_min", 91.0, "lon_max", 92.0, "covered", true),
                    Map.of("tile", "N27E088", "lat_min", 27.0, "lat_max", 28.0, "lon_min", 88.0, "lon_max", 89.0, "covered", true)
            );
        }
    }

    /**
     * Legacy zone feature map overload for backwards compatibility.
     */
    @Retryable(retryFor = Exception.class, maxAttempts = 3, backoff = @Backoff(delay = 500, multiplier = 2))
    public PredictionRequest requestPrediction(String zoneCode, Map<String, Object> features) {
        try {
            return mlRiskWebClient.post()
                    .uri("/predict")
                    .bodyValue(Map.of("zoneId", zoneCode, "features", features))
                    .retrieve()
                    .bodyToMono(PredictionRequest.class)
                    .timeout(Duration.ofSeconds(10))
                    .block();
        } catch (Exception ex) {
            log.error("ML risk engine call failed for zone {}: {}", zoneCode, ex.getMessage());
            throw new ExternalServiceException("Failed to obtain prediction from ML risk engine for zone " + zoneCode, ex);
        }
    }
}

