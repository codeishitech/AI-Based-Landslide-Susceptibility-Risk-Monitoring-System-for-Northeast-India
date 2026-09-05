package com.ner.landslide.controller;

import com.ner.landslide.dto.MlHealthResponse;
import com.ner.landslide.dto.MlPredictRequest;
import com.ner.landslide.dto.MlPredictResponse;
import com.ner.landslide.dto.PredictionRequest;
import com.ner.landslide.dto.PredictionResponse;
import com.ner.landslide.integration.MlRiskClient;
import com.ner.landslide.service.PredictionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/predictions")
@RequiredArgsConstructor
@Tag(name = "Predictions", description = "Endpoints for AI/ML landslide risk predictions and monitoring")
public class PredictionController {

    private final PredictionService predictionService;
    private final MlRiskClient mlRiskClient;

    @PostMapping
    @Operation(summary = "Ingest a fresh risk prediction from the ML service and trigger evaluation/alerting")
    public ResponseEntity<PredictionResponse> ingest(@Valid @RequestBody PredictionRequest request) {
        PredictionResponse response = predictionService.ingest(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/predict")
    @Operation(summary = "Query real-time landslide risk prediction for latitude, longitude, and optional date")
    public ResponseEntity<MlPredictResponse> predict(@Valid @RequestBody MlPredictRequest request) {
        MlPredictResponse response = predictionService.predictRisk(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/health")
    @Operation(summary = "Get status of the AI/ML risk model engine and rainfall data coverage")
    public ResponseEntity<MlHealthResponse> getHealth() {
        return ResponseEntity.ok(mlRiskClient.checkHealth());
    }

    @GetMapping("/coverage")
    @Operation(summary = "Get current DEM tile bounding boxes so frontend maps can display covered regions")
    public ResponseEntity<Object> getCoverage() {
        return ResponseEntity.ok(mlRiskClient.getCoverage());
    }
}

