package com.ner.landslide.scheduler;

import com.ner.landslide.service.AlertService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Housekeeping job that expires ACTIVE alerts whose expiresAt has passed.
 * The actual risk-evaluation-to-alert pipeline runs synchronously inside
 * PredictionService.ingest() whenever a fresh prediction arrives (either
 * from the public endpoint or PredictionRefreshJob); this job only handles
 * time-based cleanup so stale alerts don't linger as ACTIVE indefinitely.
 */
@Component
@RequiredArgsConstructor
public class RiskEvaluationJob {

    private static final Logger log = LoggerFactory.getLogger(RiskEvaluationJob.class);

    private final AlertService alertService;

    @Scheduled(fixedRateString = "${app.jobs.alert-expiry-check-interval-ms:600000}")
    public void expireStaleAlerts() {
        int expired = alertService.expireStaleAlerts();
        if (expired > 0) {
            log.info("Expired {} stale alert(s)", expired);
        }
    }
}
