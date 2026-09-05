package com.ner.landslide.dto;

import com.ner.landslide.entity.enums.ReportSeverity;
import com.ner.landslide.entity.enums.ReportType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Payload for POST /api/v1/reports and POST /api/v1/reports/sync. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FieldReportRequest {

    /** Optional idempotency key supplied by offline-capable clients. */
    private String clientGeneratedId;

    @NotNull(message = "latitude is required")
    @DecimalMin(value = "-90.0")
    @DecimalMax(value = "90.0")
    private Double latitude;

    @NotNull(message = "longitude is required")
    @DecimalMin(value = "-180.0")
    @DecimalMax(value = "180.0")
    private Double longitude;

    @NotNull(message = "type is required")
    private ReportType type;

    @NotNull(message = "severity is required")
    private ReportSeverity severity;

    @Size(max = 2000, message = "description must be under 2000 characters")
    private String description;

    private String mediaUrl;
}
