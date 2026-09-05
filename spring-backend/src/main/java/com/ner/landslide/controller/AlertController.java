package com.ner.landslide.controller;

import com.ner.landslide.entity.Alert;
import com.ner.landslide.service.AlertService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/alerts")
@RequiredArgsConstructor
@Tag(name = "Alerts", description = "Early-warning alerts generated from risk evaluations")
public class AlertController {

    private final AlertService alertService;

    @GetMapping("/active")
    @Operation(summary = "List all currently active alerts")
    public List<com.ner.landslide.dto.AlertResponse> getActive() {
        return alertService.getActiveAlerts().stream().map(alertService::toResponse).toList();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a single alert by id")
    public com.ner.landslide.dto.AlertResponse getById(@PathVariable UUID id) {
        return alertService.toResponse(alertService.getOrThrow(id));
    }

    @PutMapping("/{id}/acknowledge")
    @Operation(summary = "Acknowledge an active alert (admin only)")
    public com.ner.landslide.dto.AlertResponse acknowledge(@PathVariable UUID id) {
        Alert alert = alertService.acknowledge(id);
        return alertService.toResponse(alert);
    }

    @PutMapping("/{id}/resolve")
    @Operation(summary = "Manually resolve an alert (admin only)")
    public com.ner.landslide.dto.AlertResponse resolve(@PathVariable UUID id) {
        Alert alert = alertService.resolve(id);
        return alertService.toResponse(alert);
    }
}
