package com.ner.landslide.integration;

import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.Map;
import java.util.Random;

/**
 * Integration layer for satellite-derived features (soil moisture, terrain
 * change, vegetation index, etc.) feeding the ML risk engine.
 *
 * A real satellite/EO provider is not wired up in this environment, so this
 * client falls back to a deterministic mock feed when {@code app.satellite-service.mock-enabled}
 * is true (the default), keeping the interface stable so a real provider can
 * be dropped in later without touching callers.
 */
@Component
@RequiredArgsConstructor
public class SatelliteClient {

    private static final Logger log = LoggerFactory.getLogger(SatelliteClient.class);
    private static final Random RANDOM = new Random();

    private final WebClient satelliteWebClient;

    @Value("${app.satellite-service.mock-enabled:true}")
    private boolean mockEnabled;

    public SatelliteFeatures fetchFeatures(String zoneCode, double latitude, double longitude) {
        if (mockEnabled) {
            return mockFeatures(zoneCode);
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> raw = satelliteWebClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/features")
                            .queryParam("zone", zoneCode)
                            .queryParam("lat", latitude)
                            .queryParam("lon", longitude)
                            .build())
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofSeconds(10))
                    .block();
            return normalize(raw);
        } catch (Exception ex) {
            log.warn("Satellite provider call failed for zone {}, falling back to mock: {}", zoneCode, ex.getMessage());
            return mockFeatures(zoneCode);
        }
    }

    private SatelliteFeatures normalize(Map<String, Object> raw) {
        return SatelliteFeatures.builder()
                .soilMoisturePercent(asDouble(raw.get("soil_moisture_pct"), 30.0))
                .slopeDegrees(asDouble(raw.get("slope_degrees"), 25.0))
                .elevationMeters(asDouble(raw.get("elevation_m"), 800.0))
                .vegetationIndex(asDouble(raw.get("vegetation_index"), 0.5))
                .terrainChangeScore(asDouble(raw.get("terrain_change_score"), 0.1))
                .build();
    }

    private SatelliteFeatures mockFeatures(String zoneCode) {
        // Deterministic-ish mock, seeded by zone code so repeated calls for the
        // same zone in a dev/test session return comparable values.
        Random seeded = new Random(zoneCode.hashCode());
        return SatelliteFeatures.builder()
                .soilMoisturePercent(20 + seeded.nextDouble() * 40)
                .slopeDegrees(10 + seeded.nextDouble() * 35)
                .elevationMeters(200 + seeded.nextDouble() * 1800)
                .vegetationIndex(seeded.nextDouble())
                .terrainChangeScore(seeded.nextDouble() * 0.3)
                .build();
    }

    private Double asDouble(Object value, double fallback) {
        if (value instanceof Number n) return n.doubleValue();
        return fallback;
    }

    @Getter
    @Builder
    public static class SatelliteFeatures {
        private Double soilMoisturePercent;
        private Double slopeDegrees;
        private Double elevationMeters;
        private Double vegetationIndex;
        private Double terrainChangeScore;
    }
}
