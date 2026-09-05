package com.ner.landslide.service;

import com.ner.landslide.dto.AlertResponse;
import com.ner.landslide.entity.Alert;
import com.ner.landslide.entity.RiskZone;
import com.ner.landslide.entity.enums.AlertSeverity;
import com.ner.landslide.entity.enums.AlertStatus;
import com.ner.landslide.entity.enums.RiskLevel;
import com.ner.landslide.exception.ResourceNotFoundException;
import com.ner.landslide.notification.SmsNotificationService;
import com.ner.landslide.repository.AlertRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AlertService {

    private static final Logger log =
            LoggerFactory.getLogger(AlertService.class);

    private static final int DEDUP_WINDOW_HOURS = 6;

    private final AlertRepository alertRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final SmsNotificationService smsNotificationService;

    /**
     * Evaluates whether a newly computed risk level warrants a new alert.
     *
     * HIGH and CRITICAL levels generate alerts.
     * LOW/MEDIUM levels resolve currently active alerts.
     */
    @Transactional
    public Alert evaluateAndMaybeCreateAlert(
            RiskZone zone,
            RiskLevel newLevel,
            double compositeScore) {

        if (newLevel == RiskLevel.LOW
                || newLevel == RiskLevel.MEDIUM) {

            autoResolveActiveAlerts(
                    zone.getId(),
                    "Risk level dropped to " + newLevel
            );

            return null;
        }

        Instant dedupSince = Instant.now()
                .minus(DEDUP_WINDOW_HOURS, ChronoUnit.HOURS);

        boolean recentDuplicate =
                alertRepository
                        .existsByRiskZoneIdAndStatusAndCreatedAtAfter(
                                zone.getId(),
                                AlertStatus.ACTIVE,
                                dedupSince
                        );

        if (recentDuplicate) {

            log.debug(
                    "Skipping duplicate alert for zone {} "
                            + "(already has an active alert within {}h)",
                    zone.getCode(),
                    DEDUP_WINDOW_HOURS
            );

            return null;
        }

        AlertSeverity severity =
                newLevel == RiskLevel.CRITICAL
                        ? AlertSeverity.CRITICAL
                        : AlertSeverity.HIGH;

        Alert alert = Alert.builder()
                .riskZone(zone)
                .severity(severity)
                .title(buildTitle(zone, newLevel))
                .message(
                        buildMessage(
                                zone,
                                newLevel,
                                compositeScore
                        )
                )
                .affectedRadius(
                        newLevel == RiskLevel.CRITICAL
                                ? 5.0
                                : 2.5
                )
                .status(AlertStatus.ACTIVE)
                .expiresAt(
                        Instant.now()
                                .plus(24, ChronoUnit.HOURS)
                )
                .build();

        // Save alert in database
        Alert saved = alertRepository.save(alert);

        // Publish real-time event
        publishEvent("ALERT_TRIGGERED", saved);

        // Send SMS early warning if SMS is enabled/configured
        try {
            smsNotificationService.sendEarlyWarning(saved);
        } catch (Exception ex) {
            // SMS failure must never break alert creation
            log.warn(
                    "Failed to send SMS notification for alert {}: {}",
                    saved.getId(),
                    ex.getMessage()
            );
        }

        log.info(
                "Alert created for zone {} at severity {}",
                zone.getCode(),
                severity
        );

        return saved;
    }

    @Transactional
    public void autoResolveActiveAlerts(
            UUID zoneId,
            String reason) {

        List<Alert> active =
                alertRepository
                        .findByRiskZoneIdAndStatus(
                                zoneId,
                                AlertStatus.ACTIVE
                        );

        for (Alert alert : active) {

            alert.setStatus(AlertStatus.RESOLVED);
            alert.setResolvedAt(Instant.now());

            alertRepository.save(alert);

            publishEvent(
                    "ALERT_RESOLVED",
                    alert
            );
        }

        if (!active.isEmpty()) {

            log.info(
                    "Auto-resolved {} alert(s) for zone {}: {}",
                    active.size(),
                    zoneId,
                    reason
            );
        }
    }

    @Transactional
    public Alert acknowledge(UUID alertId) {

        Alert alert = getOrThrow(alertId);

        alert.setStatus(
                AlertStatus.ACKNOWLEDGED
        );

        return alertRepository.save(alert);
    }

    @Transactional
    public Alert resolve(UUID alertId) {

        Alert alert = getOrThrow(alertId);

        alert.setStatus(
                AlertStatus.RESOLVED
        );

        alert.setResolvedAt(
                Instant.now()
        );

        Alert saved =
                alertRepository.save(alert);

        publishEvent(
                "ALERT_RESOLVED",
                saved
        );

        return saved;
    }

    public List<Alert> getActiveAlerts() {

        return alertRepository
                .findByStatusOrderByCreatedAtDesc(
                        AlertStatus.ACTIVE
                );
    }

    public Alert getOrThrow(UUID alertId) {

        return alertRepository
                .findById(alertId)
                .orElseThrow(
                        () -> new ResourceNotFoundException(
                                "Alert not found: " + alertId
                        )
                );
    }

    /**
     * Expires stale active alerts whose expiresAt has passed.
     */
    @Transactional
    public int expireStaleAlerts() {

        List<Alert> expired =
                alertRepository
                        .findExpiredButStillActive(
                                Instant.now()
                        );

        for (Alert alert : expired) {

            alert.setStatus(
                    AlertStatus.EXPIRED
            );

            alertRepository.save(alert);
        }

        return expired.size();
    }

    public AlertResponse toResponse(Alert alert) {

        return AlertResponse.builder()
                .id(alert.getId())
                .zoneCode(
                        alert.getRiskZone().getCode()
                )
                .zoneName(
                        alert.getRiskZone().getName()
                )
                .severity(
                        alert.getSeverity()
                )
                .title(
                        alert.getTitle()
                )
                .message(
                        alert.getMessage()
                )
                .affectedRadius(
                        alert.getAffectedRadius()
                )
                .status(
                        alert.getStatus()
                )
                .createdAt(
                        alert.getCreatedAt()
                )
                .expiresAt(
                        alert.getExpiresAt()
                )
                .build();
    }

    private String buildTitle(
            RiskZone zone,
            RiskLevel level) {

        return (
                level == RiskLevel.CRITICAL
                        ? "CRITICAL landslide risk: "
                        : "High landslide risk: "
        ) + zone.getName();
    }

    private String buildMessage(
            RiskZone zone,
            RiskLevel level,
            double compositeScore) {

        return String.format(
                "%s risk level detected in %s, %s "
                        + "(composite score %.2f). Residents and "
                        + "authorities in the area should follow "
                        + "local disaster-management guidance.",
                level,
                zone.getName(),
                zone.getDistrict(),
                compositeScore
        );
    }

    private void publishEvent(
            String topic,
            Alert alert) {

        try {

            kafkaTemplate.send(
                    topic,
                    alert.getRiskZone().getCode(),
                    "{\"alertId\":\""
                            + alert.getId()
                            + "\",\"zone\":\""
                            + alert.getRiskZone().getCode()
                            + "\",\"severity\":\""
                            + alert.getSeverity()
                            + "\",\"status\":\""
                            + alert.getStatus()
                            + "\"}"
            );

        } catch (Exception ex) {

            // Kafka being unavailable shouldn't block
            // the core alert workflow.
            log.warn(
                    "Failed to publish {} event for alert {}: {}",
                    topic,
                    alert.getId(),
                    ex.getMessage()
            );
        }
    }
}