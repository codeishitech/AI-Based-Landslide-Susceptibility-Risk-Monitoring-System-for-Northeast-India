package com.ner.landslide.controller;

import com.ner.landslide.dto.RiskZoneResponse;
import com.ner.landslide.entity.RiskZone;
import com.ner.landslide.service.RiskZoneService;
import com.ner.landslide.util.GeoUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/risk-zones")
@RequiredArgsConstructor
@Tag(name = "Risk Zones", description = "Geographically defined landslide-prone zones")
public class RiskZoneController {

    private final RiskZoneService riskZoneService;

    @GetMapping
    @Operation(summary = "List all risk zones")
    public ResponseEntity<List<RiskZoneResponse>> getAll() {
        List<RiskZoneResponse> response = riskZoneService.getAll().stream()
                .map(riskZoneService::toResponse)
                .toList();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a risk zone by id")
    public ResponseEntity<RiskZoneResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(riskZoneService.toResponse(riskZoneService.getByIdOrThrow(id)));
    }

    @GetMapping("/code/{code}")
    @Operation(summary = "Get a risk zone by its external code")
    public ResponseEntity<RiskZoneResponse> getByCode(@PathVariable String code) {
        return ResponseEntity.ok(riskZoneService.toResponse(riskZoneService.getByCodeOrThrow(code)));
    }

    @GetMapping("/high-risk")
    @Operation(summary = "List zones currently at HIGH or CRITICAL risk")
    public ResponseEntity<List<RiskZoneResponse>> getHighRisk() {
        List<RiskZoneResponse> response = riskZoneService.getHighRisk().stream()
                .map(riskZoneService::toResponse)
                .toList();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/district")
    @Operation(summary = "List zones within a given state/district")
    public ResponseEntity<List<RiskZoneResponse>> getByDistrict(@RequestParam String state,
                                                                 @RequestParam String district) {
        List<RiskZoneResponse> response = riskZoneService.getByDistrict(state, district).stream()
                .map(riskZoneService::toResponse)
                .toList();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/nearby")
    @Operation(summary = "Find risk zones within a radius (km) of a coordinate, nearest first")
    public ResponseEntity<List<RiskZoneResponse>> getNearby(@RequestParam double lat,
                                                              @RequestParam double lng,
                                                              @RequestParam(defaultValue = "25") double radiusKm) {
        List<RiskZoneResponse> response = riskZoneService.findNearby(lat, lng, radiusKm).stream()
                .map(zone -> riskZoneService.toResponseWithDistance(zone, lat, lng))
                .toList();
        return ResponseEntity.ok(response);
    }

    @PostMapping
    @Operation(summary = "Create a new risk zone (admin only)")
    public ResponseEntity<RiskZoneResponse> create(@RequestBody CreateRiskZoneRequest request) {
        RiskZone zone = RiskZone.builder()
                .code(request.getCode())
                .name(request.getName())
                .state(request.getState())
                .district(request.getDistrict())
                .centroid(GeoUtils.point(request.getLatitude(), request.getLongitude()))
                .populationExposure(request.getPopulationExposure())
                .infrastructureCriticality(request.getInfrastructureCriticality() == null
                        ? 1 : request.getInfrastructureCriticality())
                .nearbyVillages(request.getNearbyVillages())
                .affectedRoads(request.getAffectedRoads())
                .build();
        RiskZone saved = riskZoneService.create(zone);
        return ResponseEntity.status(HttpStatus.CREATED).body(riskZoneService.toResponse(saved));
    }

    @PatchMapping("/{id}/exposure")
    @Operation(summary = "Update population/infrastructure exposure metadata for a zone (admin only)")
    public ResponseEntity<RiskZoneResponse> updateExposure(@PathVariable UUID id,
                                                             @RequestParam(required = false) Integer populationExposure,
                                                             @RequestParam(required = false) Integer infrastructureCriticality) {
        RiskZone updated = riskZoneService.updateExposure(id, populationExposure, infrastructureCriticality);
        return ResponseEntity.ok(riskZoneService.toResponse(updated));
    }

    @Getter
    @Setter
    public static class CreateRiskZoneRequest {
        @NotBlank
        private String code;
        @NotBlank
        private String name;
        @NotBlank
        private String state;
        @NotBlank
        private String district;
        @NotNull
        private Double latitude;
        @NotNull
        private Double longitude;
        private Integer populationExposure;
        private Integer infrastructureCriticality;
        private String nearbyVillages;
        private String affectedRoads;
    }
}
