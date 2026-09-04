package com.ner.landslide.integration;

import com.ner.landslide.exception.ExternalServiceException;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.Map;

/**
 * Client for the external weather data provider referenced by WEATHER_API_URL
 * / WEATHER_API_KEY. Normalizes the provider's response into a small internal
 * DTO so WeatherService and WeatherIngestionJob don't depend on the third
 * party's schema directly, keeping the provider swappable.
 */
@Component
@RequiredArgsConstructor
public class WeatherClient {

    private static final Logger log = LoggerFactory.getLogger(WeatherClient.class);

    private final WebClient weatherWebClient;

    @Value("${app.weather-service.api-key:}")
    private String apiKey;

    @Value("${app.weather-service.url:}")
    private String weatherServiceUrl;

    @Retryable(retryFor = Exception.class, maxAttempts = 3, backoff = @Backoff(delay = 500, multiplier = 2))
    public WeatherObservation fetchCurrentWeather(double latitude, double longitude) {
        if (apiKey == null || apiKey.isBlank() || (weatherServiceUrl != null && weatherServiceUrl.contains("example.com"))) {
            log.debug("Weather API key not configured or using placeholder URL; returning mock weather observation for ({}, {})", latitude, longitude);
            return WeatherObservation.builder()
                    .rainfallMm(0.0)
                    .forecastRainfallMm(0.0)
                    .temperatureCelsius(24.0)
                    .humidityPercent(75.0)
                    .warningLevel("NONE")
                    .source("mock-weather-provider")
                    .build();
        }

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> raw = weatherWebClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/current")
                            .queryParam("lat", latitude)
                            .queryParam("lon", longitude)
                            .queryParam("key", apiKey)
                            .build())
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofSeconds(6))
                    .block();

            return normalize(raw);
        } catch (Exception ex) {
            log.error("Weather API call failed for ({}, {}): {}", latitude, longitude, ex.getMessage());
            throw new ExternalServiceException("Failed to fetch weather data", ex);
        }
    }

    private WeatherObservation normalize(Map<String, Object> raw) {
        if (raw == null) {
            throw new ExternalServiceException("Weather provider returned an empty response");
        }
        return WeatherObservation.builder()
                .rainfallMm(asDouble(raw.get("rainfall_mm")))
                .forecastRainfallMm(asDouble(raw.get("forecast_rainfall_mm")))
                .temperatureCelsius(asDouble(raw.get("temperature_c")))
                .humidityPercent(asDouble(raw.get("humidity_pct")))
                .warningLevel((String) raw.getOrDefault("warning_level", null))
                .source("external-weather-api")
                .build();
    }

    private Double asDouble(Object value) {
        if (value == null) return null;
        if (value instanceof Number n) return n.doubleValue();
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Getter
    @Builder
    public static class WeatherObservation {
        private Double rainfallMm;
        private Double forecastRainfallMm;
        private Double temperatureCelsius;
        private Double humidityPercent;
        private String warningLevel;
        private String source;
    }
}
