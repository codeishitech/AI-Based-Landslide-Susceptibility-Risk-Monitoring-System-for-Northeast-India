package com.ner.landslide.service;

import com.ner.landslide.entity.RiskZone;
import com.ner.landslide.entity.WeatherData;
import com.ner.landslide.integration.WeatherClient;
import com.ner.landslide.repository.WeatherDataRepository;
import com.ner.landslide.util.GeoUtils;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Ingests and exposes weather observations per risk zone. */
@Service
@RequiredArgsConstructor
public class WeatherService {

    private static final Logger log = LoggerFactory.getLogger(WeatherService.class);

    private final WeatherDataRepository weatherDataRepository;
    private final WeatherClient weatherClient;
    private final KafkaTemplate<String, String> kafkaTemplate;

    /** Fetches fresh weather for a zone from the external provider and persists it. */
    @Transactional
    public WeatherData ingestForZone(RiskZone zone) {
        double lat = GeoUtils.latitudeOf(zone.getCentroid());
        double lng = GeoUtils.longitudeOf(zone.getCentroid());

        WeatherClient.WeatherObservation obs = weatherClient.fetchCurrentWeather(lat, lng);

        WeatherData data = WeatherData.builder()
                .riskZone(zone)
                .rainfallMm(obs.getRainfallMm())
                .forecastRainfallMm(obs.getForecastRainfallMm())
                .temperatureCelsius(obs.getTemperatureCelsius())
                .humidityPercent(obs.getHumidityPercent())
                .warningLevel(obs.getWarningLevel())
                .source(obs.getSource())
                .observedAt(Instant.now())
                .build();

        WeatherData saved = weatherDataRepository.save(data);
        publishWeatherUpdated(zone, saved);
        return saved;
    }

    public Optional<WeatherData> getLatestForZone(UUID zoneId) {
        return weatherDataRepository.findFirstByRiskZoneIdOrderByObservedAtDesc(zoneId);
    }

    private void publishWeatherUpdated(RiskZone zone, WeatherData data) {
        try {
            kafkaTemplate.send("WEATHER_UPDATED", zone.getCode(),
                    "{\"zone\":\"" + zone.getCode() + "\",\"rainfallMm\":" + data.getRainfallMm() + "}");
        } catch (Exception ex) {
            log.warn("Failed to publish WEATHER_UPDATED for zone {}: {}", zone.getCode(), ex.getMessage());
        }
    }
}
