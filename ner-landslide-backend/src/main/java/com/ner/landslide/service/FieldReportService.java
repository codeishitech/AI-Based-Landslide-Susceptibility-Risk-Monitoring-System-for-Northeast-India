package com.ner.landslide.service;

import com.ner.landslide.dto.FieldReportRequest;
import com.ner.landslide.dto.FieldReportResponse;
import com.ner.landslide.entity.FieldReport;
import com.ner.landslide.entity.RiskZone;
import com.ner.landslide.entity.User;
import com.ner.landslide.entity.enums.ReportStatus;
import com.ner.landslide.exception.ResourceNotFoundException;
import com.ner.landslide.repository.FieldReportRepository;
import com.ner.landslide.repository.RiskZoneRepository;
import com.ner.landslide.util.GeoUtils;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Handles citizen/field-officer incident reports, including the offline-sync
 * flow: mobile clients may queue reports while offline and later POST a batch
 * with client-generated idempotency keys, so repeated sync attempts after a
 * dropped connection never create duplicate rows.
 */
@Service
@RequiredArgsConstructor
public class FieldReportService {

    private static final Logger log = LoggerFactory.getLogger(FieldReportService.class);
    private static final double NEAREST_ZONE_SEARCH_RADIUS_KM = 25.0;

    private final FieldReportRepository fieldReportRepository;
    private final RiskZoneRepository riskZoneRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Transactional
    public FieldReport submit(FieldReportRequest request, User submittedBy) {
        // Idempotency: if this client-generated id was already synced, return the existing row.
        if (request.getClientGeneratedId() != null && !request.getClientGeneratedId().isBlank()) {
            Optional<FieldReport> existing = fieldReportRepository.findByClientGeneratedId(request.getClientGeneratedId());
            if (existing.isPresent()) {
                log.debug("Duplicate sync for clientGeneratedId {}, returning existing report", request.getClientGeneratedId());
                return existing.get();
            }
        }

        RiskZone nearestZone = riskZoneRepository
                .findNearby(request.getLatitude(), request.getLongitude(), NEAREST_ZONE_SEARCH_RADIUS_KM * 1000.0)
                .stream()
                .findFirst()
                .orElse(null);

        FieldReport report = FieldReport.builder()
                .clientGeneratedId(request.getClientGeneratedId())
                .reportedBy(submittedBy)
                .riskZone(nearestZone)
                .reportType(request.getType())
                .severity(request.getSeverity())
                .description(request.getDescription())
                .location(GeoUtils.point(request.getLatitude(), request.getLongitude()))
                .mediaUrl(request.getMediaUrl())
                .status(ReportStatus.PENDING)
                .build();

        FieldReport saved = fieldReportRepository.save(report);
        publishReportCreated(saved);
        log.info("Field report {} submitted (type={}, severity={}, zone={})",
                saved.getId(), saved.getReportType(), saved.getSeverity(),
                nearestZone != null ? nearestZone.getCode() : "none");
        return saved;
    }

    /** Batch sync endpoint: processes a list of queued offline reports in one call. */
    @Transactional
    public List<FieldReport> syncBatch(List<FieldReportRequest> requests, User submittedBy) {
        return requests.stream().map(r -> submit(r, submittedBy)).toList();
    }

    public List<FieldReport> getChangesSince(Instant since) {
        return fieldReportRepository.findChangesSince(since);
    }

    @Transactional
    public FieldReport updateStatus(UUID reportId, ReportStatus status) {
        FieldReport report = fieldReportRepository.findById(reportId)
                .orElseThrow(() -> new ResourceNotFoundException("Field report not found: " + reportId));
        report.setStatus(status);
        return fieldReportRepository.save(report);
    }

    public List<FieldReport> getByStatus(ReportStatus status) {
        return fieldReportRepository.findByStatus(status);
    }

    public FieldReportResponse toResponse(FieldReport report) {
        return FieldReportResponse.builder()
                .id(report.getId())
                .clientGeneratedId(report.getClientGeneratedId())
                .latitude(GeoUtils.latitudeOf(report.getLocation()))
                .longitude(GeoUtils.longitudeOf(report.getLocation()))
                .type(report.getReportType())
                .severity(report.getSeverity())
                .description(report.getDescription())
                .mediaUrl(report.getMediaUrl())
                .status(report.getStatus())
                .nearestRiskZoneCode(report.getRiskZone() != null ? report.getRiskZone().getCode() : null)
                .createdAt(report.getCreatedAt())
                .updatedAt(report.getUpdatedAt())
                .build();
    }

    private void publishReportCreated(FieldReport report) {
        try {
            kafkaTemplate.send("FIELD_REPORT_CREATED", report.getId().toString(),
                    "{\"reportId\":\"" + report.getId() + "\",\"type\":\"" + report.getReportType()
                            + "\",\"severity\":\"" + report.getSeverity() + "\"}");
        } catch (Exception ex) {
            log.warn("Failed to publish FIELD_REPORT_CREATED for report {}: {}", report.getId(), ex.getMessage());
        }
    }
}
