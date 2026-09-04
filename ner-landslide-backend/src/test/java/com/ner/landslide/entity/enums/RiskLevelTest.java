package com.ner.landslide.entity.enums;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RiskLevelTest {

    @Test
    void fromScore_classifiesBoundariesCorrectly() {
        assertEquals(RiskLevel.LOW, RiskLevel.fromScore(0.0));
        assertEquals(RiskLevel.LOW, RiskLevel.fromScore(0.39));
        assertEquals(RiskLevel.MEDIUM, RiskLevel.fromScore(0.40));
        assertEquals(RiskLevel.MEDIUM, RiskLevel.fromScore(0.69));
        assertEquals(RiskLevel.HIGH, RiskLevel.fromScore(0.70));
        assertEquals(RiskLevel.HIGH, RiskLevel.fromScore(0.84));
        assertEquals(RiskLevel.CRITICAL, RiskLevel.fromScore(0.85));
        assertEquals(RiskLevel.CRITICAL, RiskLevel.fromScore(1.0));
    }
}
