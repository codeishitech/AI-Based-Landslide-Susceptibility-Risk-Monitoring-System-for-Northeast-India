package com.ner.landslide.service;

import com.ner.landslide.entity.RiskZone;
import com.ner.landslide.entity.WeatherData;
import com.ner.landslide.entity.enums.RiskLevel;
import com.ner.landslide.repository.FieldReportRepository;
import com.ner.landslide.repository.WeatherDataRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Blends the raw ML risk score with contextual signals (recent rainfall,
 * ML confidence, recent verified field reports, population/infrastructure
 * exposure) into a single composite score used for alerting decisions.
 *
 * The ML model score is treated as the primary signal but is never trusted
 * blindly: low-confidence predictions are dampened, and corroborating field
 * reports or extreme rainfall can push a MEDIUM zone into HIGH even if the
 * raw model score alone would not have crossed that threshold.
 */
@Service
@RequiredArgsConstructor
public class RiskEvaluationService {

    private static final Logger log = LoggerFactory.getLogger(RiskEvaluationService.class);

    private final WeatherDataRepository weatherDataRepository;
    private final FieldReportRepository fieldReportRepository;

    /**
     * Computes a composite score in [0, 1] for a zone given a fresh ML
     * prediction. Weighting:
     *  - 60% raw ML risk score (dampened by confidence)
     *  - 20% recent rainfall pressure (24h observed + forecast, normalized)
     *  - 10% recent corroborating field reports (landslide/slope-movement in last 48h)
     *  - 10% exposure factor (population + infrastructure criticality)
     */
    public EvaluationResult evaluate(RiskZone zone, double mlRiskScore, double mlConfidence) {
        double confidenceAdjustedMlScore = mlRiskScore * clamp(0.5 + (mlConfidence * 0.5), 0.0, 1.0);

        double rainfallFactor = computeRainfallFactor(zone.getId());
        double reportFactor = computeFieldReportFactor(zone.getId());
        double exposureFactor = computeExposureFactor(zone);

        double composite = (confidenceAdjustedMlScore * 0.60)
                + (rainfallFactor * 0.20)
                + (reportFactor * 0.10)
                + (exposureFactor * 0.10);

        composite = clamp(composite, 0.0, 1.0);
        RiskLevel level = RiskLevel.fromScore(composite);

        log.debug("Zone {} composite evaluation: ml={} conf={} rainfall={} reports={} exposure={} => composite={} level={}",
                zone.getCode(), mlRiskScore, mlConfidence, rainfallFactor, reportFactor, exposureFactor, composite, level);

        return new EvaluationResult(composite, level);
    }

    private double computeRainfallFactor(UUID zoneId) {
        return weatherDataRepository.findFirstByRiskZoneIdOrderByObservedAtDesc(zoneId)
                .map(this::rainfallToFactor)
                .orElse(0.0);
    }

    private double rainfallToFactor(WeatherData weather) {
        double observed = weather.getRainfallMm() == null ? 0.0 : weather.getRainfallMm();
        double forecast = weather.getForecastRainfallMm() == null ? 0.0 : weather.getForecastRainfallMm();
        // Normalize against a heavy-rainfall benchmark of 150mm/24h (IMD "heavy rain" territory).
        double combined = observed + (forecast * 0.5);
        return clamp(combined / 150.0, 0.0, 1.0);
    }

    private double computeFieldReportFactor(UUID zoneId) {
        Instant since = Instant.now().minus(48, ChronoUnit.HOURS);
        long recentReports = fieldReportRepository.countByReportTypeAndCreatedAtAfter(
                com.ner.landslide.entity.enums.ReportType.LANDSLIDE, since)
                + fieldReportRepository.countByReportTypeAndCreatedAtAfter(
                com.ner.landslide.entity.enums.ReportType.SLOPE_MOVEMENT, since)
                + fieldReportRepository.countByReportTypeAndCreatedAtAfter(
                com.ner.landslide.entity.enums.ReportType.SOIL_CRACK, since);
        // 3+ corroborating reports saturates this factor.
        return clamp(recentReports / 3.0, 0.0, 1.0);
    }

    private double computeExposureFactor(RiskZone zone) {
        double populationScore = zone.getPopulationExposure() == null
                ? 0.0
                : clamp(zone.getPopulationExposure() / 5000.0, 0.0, 1.0);
        double infraScore = zone.getInfrastructureCriticality() == null
                ? 0.0
                : clamp(zone.getInfrastructureCriticality() / 5.0, 0.0, 1.0);
        return (populationScore * 0.6) + (infraScore * 0.4);
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public record EvaluationResult(double compositeScore, RiskLevel riskLevel) {
    }
}
