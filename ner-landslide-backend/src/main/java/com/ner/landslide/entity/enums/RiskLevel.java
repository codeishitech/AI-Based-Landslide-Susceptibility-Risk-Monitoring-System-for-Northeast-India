package com.ner.landslide.entity.enums;

/** Overall landslide risk classification for a RiskZone. */
public enum RiskLevel {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL;

    /**
     * Derive a RiskLevel from a normalized composite risk score in the range [0, 1].
     * Thresholds are centralized here so risk-band logic stays consistent
     * across RiskEvaluationService and any reporting/analytics code.
     */
    public static RiskLevel fromScore(double score) {
        if (score >= 0.85) return CRITICAL;
        if (score >= 0.70) return HIGH;
        if (score >= 0.40) return MEDIUM;
        return LOW;
    }
}
