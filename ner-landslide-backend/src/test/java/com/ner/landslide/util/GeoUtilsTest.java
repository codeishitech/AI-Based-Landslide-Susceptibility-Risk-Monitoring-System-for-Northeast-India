package com.ner.landslide.util;

import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Point;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeoUtilsTest {

    @Test
    void point_roundTripsLatitudeAndLongitude() {
        Point p = GeoUtils.point(25.5788, 91.8933); // Shillong
        assertEquals(25.5788, GeoUtils.latitudeOf(p), 1e-9);
        assertEquals(91.8933, GeoUtils.longitudeOf(p), 1e-9);
        assertEquals(GeoUtils.SRID, p.getSRID());
    }

    @Test
    void haversineKm_zeroForSamePoint() {
        double distance = GeoUtils.haversineKm(25.5788, 91.8933, 25.5788, 91.8933);
        assertEquals(0.0, distance, 1e-6);
    }

    @Test
    void haversineKm_approximatesKnownDistance() {
        // Shillong to Gangtok is roughly 320-350 km as the crow flies.
        double distance = GeoUtils.haversineKm(25.5788, 91.8933, 27.3389, 88.6138);
        assertTrue(distance > 300 && distance < 400,
                "Expected distance between 300 and 400 km, got " + distance);
    }
}
