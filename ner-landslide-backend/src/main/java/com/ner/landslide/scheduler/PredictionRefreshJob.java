package com.ner.landslide.scheduler;

import com.ner.landslide.dto.MlPredictResponse;
import com.ner.landslide.dto.PredictionRequest;
import com.ner.landslide.entity.RiskZone;
import com.ner.landslide.integration.MlRiskClient;
import com.ner.landslide.repository.RiskZoneRepository;
import com.ner.landslide.service.PredictionService;
import com.ner.landslide.service.SatelliteService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Map;

/**
 * Periodically requests a fresh prediction from the ML risk engine for every
 * risk zone by passing its centroid coordinates and today's date to POST /predict,
 * then routes the result through PredictionService.ingest() so evaluation and
 * alerting remain consistent across the platform.
 */
@Component
@RequiredArgsConstructor
public class PredictionRefreshJob {

    private static final Logger log = LoggerFactory.getLogger(PredictionRefreshJob.class);

    private final RiskZoneRepository riskZoneRepository;
    private final SatelliteService satelliteService;
    private final MlRiskClient mlRiskClient;
    private final PredictionService predictionService;

    @Scheduled(fixedRateString = "${app.jobs.prediction-refresh-interval-ms:3600000}")
    public void refreshAllZones() {
        log.info("Starting scheduled ML prediction refresh run");
        int success = 0;
        int failed = 0;
        String today = LocalDate.now().toString();

        for (RiskZone zone : riskZoneRepository.findAll()) {
            try {
                MlPredictResponse mlResp = null;
                if (zone.getCentroid() != null) {
                    double lat = zone.getCentroid().getY();
                    double lng = zone.getCentroid().getX();
                    mlResp = mlRiskClient.predict(lat, lng, today);
                }

                if (mlResp != null) {
                    PredictionRequest req = new PredictionRequest();
                    req.setZoneId(zone.getCode());
                    req.setRiskScore(mlResp.getRiskScore());
                    req.setConfidence(mlResp.getConfidence());
                    req.setRiskLevel(mlResp.getRiskLevel());
                    req.setPredictionWindow("24h");
                    req.setModelVersion("SIH26001-v1");

                    predictionService.ingest(req);
                    success++;
                } else {
                    // Fallback to feature map if centroid is missing
                    Map<String, Object> features = satelliteService.buildFeatureSet(zone);
                    PredictionRequest prediction = mlRiskClient.requestPrediction(zone.getCode(), features);
                    if (prediction != null) {
                        prediction.setZoneId(zone.getCode());
                        predictionService.ingest(prediction);
                        success++;
                    } else {
                        failed++;
                    }
                }
            } catch (Exception ex) {
                failed++;
                log.warn("Prediction refresh failed for zone {}: {}", zone.getCode(), ex.getMessage());
            }
        }
        log.info("Prediction refresh run complete: {} succeeded, {} failed", success, failed);
    }
}

