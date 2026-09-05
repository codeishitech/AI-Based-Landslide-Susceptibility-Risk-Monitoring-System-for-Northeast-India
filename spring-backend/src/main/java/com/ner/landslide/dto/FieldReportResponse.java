package com.ner.landslide.dto;

import com.ner.landslide.entity.enums.ReportSeverity;
import com.ner.landslide.entity.enums.ReportStatus;
import com.ner.landslide.entity.enums.ReportType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FieldReportResponse {

    private UUID id;
    private String clientGeneratedId;
    private double latitude;
    private double longitude;
    private ReportType type;
    private ReportSeverity severity;
    private String description;
    private String mediaUrl;
    private ReportStatus status;
    private String nearestRiskZoneCode;
    private Instant createdAt;
    private Instant updatedAt;
}
