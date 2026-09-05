package com.ner.landslide.controller;

import com.ner.landslide.dto.AlertResponse;
import com.ner.landslide.entity.Alert;
import com.ner.landslide.notification.MultilingualNotificationService;
import com.ner.landslide.notification.NotificationLanguage;
import com.ner.landslide.service.AlertService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/alerts")
@RequiredArgsConstructor
public class AlertController {

    private final AlertService alertService;

    private final MultilingualNotificationService
            multilingualNotificationService =
            new MultilingualNotificationService();

    /**
     * Get all currently active alerts.
     *
     * Language:
     * en = English
     * hi = Hindi
     * as = Assamese
     *
     * Example:
     * /api/v1/alerts/active?lang=hi
     */
    @GetMapping("/active")
    public List<AlertResponse> getActiveAlerts(
            @RequestParam(defaultValue = "en") String lang) {

        NotificationLanguage language =
                NotificationLanguage.fromCode(lang);

        return alertService.getActiveAlerts()
                .stream()
                .map(alert ->
                        toLocalizedResponse(alert, language))
                .toList();
    }

    /**
     * Get a single alert by ID.
     */
    @GetMapping("/{id}")
    public AlertResponse getAlert(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "en") String lang) {

        Alert alert = alertService.getOrThrow(id);

        NotificationLanguage language =
                NotificationLanguage.fromCode(lang);

        return toLocalizedResponse(alert, language);
    }

    /**
     * Acknowledge an alert.
     */
    @PutMapping("/{id}/acknowledge")
    public AlertResponse acknowledge(
            @PathVariable UUID id) {

        return alertService.toResponse(
                alertService.acknowledge(id)
        );
    }

    /**
     * Resolve an alert.
     */
    @PutMapping("/{id}/resolve")
    public AlertResponse resolve(
            @PathVariable UUID id) {

        return alertService.toResponse(
                alertService.resolve(id)
        );
    }

    /**
     * Convert Alert entity to localized API response.
     */
    private AlertResponse toLocalizedResponse(
            Alert alert,
            NotificationLanguage language) {

        MultilingualNotificationService.NotificationMessage
                notification =
                multilingualNotificationService
                        .translate(alert, language);

        AlertResponse response =
                alertService.toResponse(alert);

        response.setTitle(notification.title());
        response.setMessage(notification.message());

        return response;
    }
}