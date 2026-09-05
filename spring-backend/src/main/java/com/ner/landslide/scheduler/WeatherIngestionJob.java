package com.ner.landslide.scheduler;

import com.ner.landslide.entity.RiskZone;
import com.ner.landslide.repository.RiskZoneRepository;
import com.ner.landslide.service.WeatherService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Refreshes weather data for every risk zone on a fixed interval. Runs
 * asynchronously so a slow weather-provider response for one zone doesn't
 * block the scheduler thread for the rest.
 */
@Component
@RequiredArgsConstructor
public class WeatherIngestionJob {

    private static final Logger log = LoggerFactory.getLogger(WeatherIngestionJob.class);

    private final RiskZoneRepository riskZoneRepository;
    private final WeatherService weatherService;

    @Scheduled(fixedRateString = "${app.jobs.weather-ingestion-interval-ms:1800000}")
    public void ingestAllZones() {
        log.info("Starting scheduled weather ingestion run");
        int success = 0;
        int failed = 0;
        for (RiskZone zone : riskZoneRepository.findAll()) {
            try {
                weatherService.ingestForZone(zone);
                success++;
            } catch (Exception ex) {
                failed++;
                log.warn("Weather ingestion failed for zone {}: {}", zone.getCode(), ex.getMessage());
            }
        }
        log.info("Weather ingestion run complete: {} succeeded, {} failed", success, failed);
    }
}
