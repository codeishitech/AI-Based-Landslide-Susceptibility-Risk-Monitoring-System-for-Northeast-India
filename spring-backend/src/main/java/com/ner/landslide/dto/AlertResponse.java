package com.ner.landslide.dto;

import com.ner.landslide.entity.enums.AlertSeverity;
import com.ner.landslide.entity.enums.AlertStatus;
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
public class AlertResponse {

    private UUID id;
    private String zoneCode;
    private String zoneName;
    private AlertSeverity severity;
    private String title;
    private String message;
    private Double affectedRadius;
    private AlertStatus status;
    private Instant createdAt;
    private Instant expiresAt;
}
