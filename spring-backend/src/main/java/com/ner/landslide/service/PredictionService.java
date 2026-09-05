package com.ner.landslide.service;

import com.ner.landslide.dto.MlPredictRequest;
import com.ner.landslide.dto.MlPredictResponse;
import com.ner.landslide.dto.PredictionRequest;
import com.ner.landslide.dto.PredictionResponse;
import com.ner.landslide.entity.Alert;
import com.ner.landslide.entity.RiskPrediction;
import com.ner.landslide.entity.RiskZone;
import com.ner.landslide.entity.enums.RiskLevel;
import com.ner.landslide.exception.InvalidPredictionException;
import com.ner.landslide.exception.ResourceNotFoundException;
import com.ner.landslide.integration.MlRiskClient;
import com.ner.landslide.repository.PredictionRepository;
import com.ner.landslide.repository.RiskZoneRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ingests predictions from the AI/ML risk engine (POST /api/v1/predictions),
 * validates them, computes the composite risk score via RiskEvaluationService,
 * updates the owning RiskZone, and triggers alerting when warranted.
 *
 * Primary integration seam between Java backend and Python/FastAPI ML service.
 */
@Service
@RequiredArgsConstructor
public class PredictionService {

    private static final Logger log = LoggerFactory.getLogger(PredictionService.class);

    private final PredictionRepository predictionRepository;
    private final RiskZoneRepository riskZoneRepository;
    private final RiskEvaluationService riskEvaluationService;
    private final AlertService alertService;
    private final MlRiskClient mlRiskClient;
    private final KafkaTemplate<String, String> kafkaTemplate;

    /**
     * Executes real-time prediction for given latitude, longitude, and optional date.
     */
    public MlPredictResponse predictRisk(MlPredictRequest request) {
        if (request.getLatitude() == null || request.getLongitude() == null) {
            throw new InvalidPredictionException("latitude and longitude are required");
        }
        return mlRiskClient.predict(request);
    }

    @Transactional
    public PredictionResponse ingest(PredictionRequest request) {
        validate(request);

        RiskZone zone = riskZoneRepository.findByCode(request.getZoneId())
                .orElseThrow(() -> new ResourceNotFoundException("Unknown risk zone code: " + request.getZoneId()));

        RiskEvaluationService.EvaluationResult evaluation =
                riskEvaluationService.evaluate(zone, request.getRiskScore(), request.getConfidence());

        RiskPrediction prediction = RiskPrediction.builder()
                .riskZone(zone)
                .riskScore(request.getRiskScore())
                .confidence(request.getConfidence())
                .riskLevel(evaluation.riskLevel())
                .compositeScore(evaluation.compositeScore())
                .predictionWindow(request.getPredictionWindow())
                .modelVersion(request.getModelVersion())
                .build();
        prediction = predictionRepository.save(prediction);

        RiskLevel previousLevel = zone.getCurrentRiskLevel();
        zone.setCurrentRiskScore(evaluation.compositeScore());
        zone.setCurrentRiskLevel(evaluation.riskLevel());
        riskZoneRepository.save(zone);

        if (previousLevel != evaluation.riskLevel()) {
            publishRiskLevelChanged(zone, previousLevel, evaluation.riskLevel());
        }

        Alert alert = alertService.evaluateAndMaybeCreateAlert(zone, evaluation.riskLevel(), evaluation.compositeScore());

        log.info("Prediction ingested for zone {}: rawScore={} composite={} level={} alertGenerated={}",
                zone.getCode(), request.getRiskScore(), evaluation.compositeScore(), evaluation.riskLevel(), alert != null);

        return toResponse(prediction, zone.getCode(), alert != null);
    }

    private void validate(PredictionRequest request) {
        if (request.getRiskScore() < 0.0 || request.getRiskScore() > 1.0) {
            throw new InvalidPredictionException("riskScore must be between 0.0 and 1.0");
        }
        if (request.getConfidence() < 0.0 || request.getConfidence() > 1.0) {
            throw new InvalidPredictionException("confidence must be between 0.0 and 1.0");
        }
        if (request.getZoneId() == null || request.getZoneId().isBlank()) {
            throw new InvalidPredictionException("zoneId is required");
        }
    }

    private void publishRiskLevelChanged(RiskZone zone, RiskLevel previous, RiskLevel current) {
        try {
            kafkaTemplate.send("RISK_LEVEL_CHANGED", zone.getCode(),
                    "{\"zone\":\"" + zone.getCode() + "\",\"previousLevel\":\"" + previous
                            + "\",\"currentLevel\":\"" + current + "\"}");
        } catch (Exception ex) {
            log.warn("Failed to publish RISK_LEVEL_CHANGED for zone {}: {}", zone.getCode(), ex.getMessage());
        }
    }

    private PredictionResponse toResponse(RiskPrediction prediction, String zoneCode, boolean alertGenerated) {
        return PredictionResponse.builder()
                .id(prediction.getId())
                .zoneCode(zoneCode)
                .riskScore(prediction.getRiskScore())
                .confidence(prediction.getConfidence())
                .compositeScore(prediction.getCompositeScore())
                .riskLevel(prediction.getRiskLevel())
                .predictionWindow(prediction.getPredictionWindow())
                .modelVersion(prediction.getModelVersion())
                .alertGenerated(alertGenerated)
                .createdAt(prediction.getCreatedAt())
                .build();
    }
}
