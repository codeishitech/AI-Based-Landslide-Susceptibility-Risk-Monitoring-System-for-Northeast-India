package com.ner.landslide.service;

import com.ner.landslide.entity.enums.AlertStatus;
import com.ner.landslide.entity.enums.RiskLevel;
import com.ner.landslide.repository.AlertRepository;
import com.ner.landslide.repository.FieldReportRepository;
import com.ner.landslide.repository.RiskZoneRepository;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.EnumMap;
import java.util.Map;

/** Provides aggregate dashboard statistics for disaster-management staff. */
@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final RiskZoneRepository riskZoneRepository;
    private final AlertRepository alertRepository;
    private final FieldReportRepository fieldReportRepository;

    @Cacheable(cacheNames = "analytics", key = "'summary'")
    public DashboardSummary getSummary() {
        Map<RiskLevel, Long> zonesByLevel = new EnumMap<>(RiskLevel.class);
        for (RiskLevel level : RiskLevel.values()) {
            zonesByLevel.put(level, riskZoneRepository.countByCurrentRiskLevel(level));
        }

        long activeAlerts = alertRepository.countByStatus(AlertStatus.ACTIVE);
        long acknowledgedAlerts = alertRepository.countByStatus(AlertStatus.ACKNOWLEDGED);

        Instant last24h = Instant.now().minus(24, ChronoUnit.HOURS);
        long totalZones = riskZoneRepository.count();

        return DashboardSummary.builder()
                .totalZones(totalZones)
                .zonesByRiskLevel(zonesByLevel)
                .activeAlerts(activeAlerts)
                .acknowledgedAlerts(acknowledgedAlerts)
                .reportsLast24h(countReportsSince(last24h))
                .generatedAt(Instant.now())
                .build();
    }

    private long countReportsSince(Instant since) {
        long total = 0;
        for (var type : com.ner.landslide.entity.enums.ReportType.values()) {
            total += fieldReportRepository.countByReportTypeAndCreatedAtAfter(type, since);
        }
        return total;
    }

    @Getter
    @Builder
    public static class DashboardSummary {
        private long totalZones;
        private Map<RiskLevel, Long> zonesByRiskLevel;
        private long activeAlerts;
        private long acknowledgedAlerts;
        private long reportsLast24h;
        private Instant generatedAt;
    }
}
