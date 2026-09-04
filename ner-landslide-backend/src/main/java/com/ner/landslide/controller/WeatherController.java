package com.ner.landslide.controller;

import com.ner.landslide.entity.WeatherData;
import com.ner.landslide.exception.ResourceNotFoundException;
import com.ner.landslide.service.RiskZoneService;
import com.ner.landslide.service.WeatherService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/weather")
@RequiredArgsConstructor
@Tag(name = "Weather", description = "Latest ingested weather observations per risk zone")
public class WeatherController {

    private final WeatherService weatherService;
    private final RiskZoneService riskZoneService;

    @GetMapping("/zone/{zoneId}")
    @Operation(summary = "Get the latest weather observation for a risk zone")
    public WeatherData getLatest(@PathVariable UUID zoneId) {
        return weatherService.getLatestForZone(zoneId)
                .orElseThrow(() -> new ResourceNotFoundException("No weather data available for zone: " + zoneId));
    }

    @GetMapping("/zone/code/{code}")
    @Operation(summary = "Get the latest weather observation for a risk zone by its code")
    public WeatherData getLatestByCode(@PathVariable String code) {
        var zone = riskZoneService.getByCodeOrThrow(code);
        return weatherService.getLatestForZone(zone.getId())
                .orElseThrow(() -> new ResourceNotFoundException("No weather data available for zone: " + code));
    }
}
