package com.ner.landslide.service;

import com.ner.landslide.entity.RiskZone;
import com.ner.landslide.entity.WeatherData;
import com.ner.landslide.integration.SatelliteClient;
import com.ner.landslide.util.GeoUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Builds the combined feature set (satellite + weather + zone metadata) that
 * gets sent to the ML risk engine for a fresh prediction. Kept as a small
 * dedicated service so PredictionRefreshJob and any future manual "recompute"
 * endpoint share exactly the same feature-assembly logic.
 */
@Service
@RequiredArgsConstructor
public class SatelliteService {

    private final SatelliteClient satelliteClient;
    private final WeatherService weatherService;

    public Map<String, Object> buildFeatureSet(RiskZone zone) {
        double lat = GeoUtils.latitudeOf(zone.getCentroid());
        double lng = GeoUtils.longitudeOf(zone.getCentroid());

        SatelliteClient.SatelliteFeatures satellite = satelliteClient.fetchFeatures(zone.getCode(), lat, lng);
        Optional<WeatherData> latestWeather = weatherService.getLatestForZone(zone.getId());

        Map<String, Object> features = new HashMap<>();
        features.put("latitude", lat);
        features.put("longitude", lng);
        features.put("soil_moisture_pct", satellite.getSoilMoisturePercent());
        features.put("slope_degrees", satellite.getSlopeDegrees());
        features.put("elevation_m", satellite.getElevationMeters());
        features.put("vegetation_index", satellite.getVegetationIndex());
        features.put("terrain_change_score", satellite.getTerrainChangeScore());

        latestWeather.ifPresent(w -> {
            features.put("rainfall_mm_24h", w.getRainfallMm());
            features.put("forecast_rainfall_mm", w.getForecastRainfallMm());
            features.put("humidity_pct", w.getHumidityPercent());
        });

        features.put("population_exposure", zone.getPopulationExposure());
        features.put("infrastructure_criticality", zone.getInfrastructureCriticality());

        return features;
    }
}
