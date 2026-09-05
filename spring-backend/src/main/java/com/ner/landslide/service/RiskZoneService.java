package com.ner.landslide.service;

import com.ner.landslide.dto.RiskZoneResponse;
import com.ner.landslide.entity.RiskZone;
import com.ner.landslide.entity.enums.RiskLevel;
import com.ner.landslide.exception.DuplicateResourceException;
import com.ner.landslide.exception.ResourceNotFoundException;
import com.ner.landslide.repository.RiskZoneRepository;
import com.ner.landslide.util.GeoUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RiskZoneService {

    private final RiskZoneRepository riskZoneRepository;

    @Transactional
    public RiskZone create(RiskZone zone) {
        if (riskZoneRepository.findByCode(zone.getCode()).isPresent()) {
            throw new DuplicateResourceException("A risk zone with code " + zone.getCode() + " already exists");
        }
        return riskZoneRepository.save(zone);
    }

    public RiskZone getByIdOrThrow(UUID id) {
        return riskZoneRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Risk zone not found: " + id));
    }

    public RiskZone getByCodeOrThrow(String code) {
        return riskZoneRepository.findByCode(code)
                .orElseThrow(() -> new ResourceNotFoundException("Risk zone not found: " + code));
    }

    @Cacheable(cacheNames = "riskZones", key = "'all'")
    public List<RiskZone> getAll() {
        return riskZoneRepository.findAll();
    }

    public List<RiskZone> getByDistrict(String state, String district) {
        return riskZoneRepository.findByStateIgnoreCaseAndDistrictIgnoreCase(state, district);
    }

    public List<RiskZone> getHighRisk() {
        return riskZoneRepository.findByCurrentRiskLevelIn(List.of(RiskLevel.HIGH, RiskLevel.CRITICAL));
    }

    public List<RiskZone> findNearby(double lat, double lng, double radiusKm) {
        return riskZoneRepository.findNearby(lat, lng, radiusKm * 1000.0);
    }

    @Transactional
    @CacheEvict(cacheNames = "riskZones", allEntries = true)
    public RiskZone updateExposure(UUID id, Integer populationExposure, Integer infrastructureCriticality) {
        RiskZone zone = getByIdOrThrow(id);
        if (populationExposure != null) zone.setPopulationExposure(populationExposure);
        if (infrastructureCriticality != null) zone.setInfrastructureCriticality(infrastructureCriticality);
        return riskZoneRepository.save(zone);
    }

    public RiskZoneResponse toResponse(RiskZone zone) {
        return RiskZoneResponse.builder()
                .id(zone.getId())
                .code(zone.getCode())
                .name(zone.getName())
                .state(zone.getState())
                .district(zone.getDistrict())
                .latitude(GeoUtils.latitudeOf(zone.getCentroid()))
                .longitude(GeoUtils.longitudeOf(zone.getCentroid()))
                .currentRiskScore(zone.getCurrentRiskScore())
                .currentRiskLevel(zone.getCurrentRiskLevel())
                .populationExposure(zone.getPopulationExposure())
                .infrastructureCriticality(zone.getInfrastructureCriticality())
                .nearbyVillages(zone.getNearbyVillages())
                .affectedRoads(zone.getAffectedRoads())
                .lastUpdated(zone.getLastUpdated())
                .build();
    }

    public RiskZoneResponse toResponseWithDistance(RiskZone zone, double fromLat, double fromLng) {
        RiskZoneResponse response = toResponse(zone);
        response.setDistanceKm(GeoUtils.haversineKm(fromLat, fromLng,
                GeoUtils.latitudeOf(zone.getCentroid()), GeoUtils.longitudeOf(zone.getCentroid())));
        return response;
    }
}
