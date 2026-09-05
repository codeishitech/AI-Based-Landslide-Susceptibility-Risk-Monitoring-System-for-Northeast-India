package com.ner.landslide.controller;

import com.ner.landslide.dto.FieldReportRequest;
import com.ner.landslide.dto.FieldReportResponse;
import com.ner.landslide.entity.FieldReport;
import com.ner.landslide.entity.Profile;
import com.ner.landslide.entity.enums.ReportStatus;
import com.ner.landslide.service.FieldReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
@Tag(name = "Field Reports", description = "Citizen and field-officer incident reports, with offline sync support")
public class FieldReportController {

    private final FieldReportService fieldReportService;

    @PostMapping
    @Operation(summary = "Submit a single field report")
    public ResponseEntity<FieldReportResponse> submit(@Valid @RequestBody FieldReportRequest request,
                                                        @AuthenticationPrincipal Profile user) {
        FieldReport report = fieldReportService.submit(request, user);
        return ResponseEntity.status(HttpStatus.CREATED).body(fieldReportService.toResponse(report));
    }

    @PostMapping("/sync")
    @Operation(summary = "Sync a batch of reports queued while offline (idempotent via clientGeneratedId)")
    public ResponseEntity<List<FieldReportResponse>> sync(@Valid @RequestBody List<FieldReportRequest> requests,
                                                            @AuthenticationPrincipal Profile user) {
        List<FieldReportResponse> response = fieldReportService.syncBatch(requests, user).stream()
                .map(fieldReportService::toResponse)
                .toList();
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/changes")
    @Operation(summary = "Fetch reports created/updated since a given timestamp (for client-side sync)")
    public List<FieldReportResponse> getChangesSince(@RequestParam Instant since) {
        return fieldReportService.getChangesSince(since).stream()
                .map(fieldReportService::toResponse)
                .toList();
    }

    @GetMapping("/status/{status}")
    @Operation(summary = "List reports by processing status")
    public List<FieldReportResponse> getByStatus(@PathVariable ReportStatus status) {
        return fieldReportService.getByStatus(status).stream()
                .map(fieldReportService::toResponse)
                .toList();
    }

    @PutMapping("/{id}/status")
    @Operation(summary = "Update a report's processing status (field officer / admin only)")
    public FieldReportResponse updateStatus(@PathVariable UUID id, @RequestParam ReportStatus status) {
        return fieldReportService.toResponse(fieldReportService.updateStatus(id, status));
    }
}
