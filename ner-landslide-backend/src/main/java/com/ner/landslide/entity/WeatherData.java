package com.ner.landslide.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** A single normalized weather observation/forecast ingested for a RiskZone. */
@Entity
@Table(name = "weather_data", indexes = {
        @Index(name = "idx_weather_zone_time", columnList = "risk_zone_id,observed_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WeatherData {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "risk_zone_id", nullable = false)
    private RiskZone riskZone;

    @Column(name = "rainfall_mm")
    private Double rainfallMm;

    @Column(name = "forecast_rainfall_mm")
    private Double forecastRainfallMm;

    @Column(name = "temperature_celsius")
    private Double temperatureCelsius;

    @Column(name = "humidity_percent")
    private Double humidityPercent;

    @Column(name = "warning_level", length = 40)
    private String warningLevel;

    @Column(name = "source", length = 60)
    private String source;

    @Column(name = "observed_at", nullable = false)
    private Instant observedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
