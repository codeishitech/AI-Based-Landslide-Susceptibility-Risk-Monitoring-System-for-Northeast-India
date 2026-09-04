package com.ner.landslide.util;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;

/**
 * Small helper for building PostGIS/JTS geometries from plain lat/lng values.
 * All geometries use SRID 4326 (WGS 84), matching the columns defined in the
 * Flyway migration.
 */
public final class GeoUtils {

    public static final int SRID = 4326;

    private static final GeometryFactory GEOMETRY_FACTORY =
            new GeometryFactory(new PrecisionModel(), SRID);

    private GeoUtils() {
    }

    /** Builds a Point in (longitude, latitude) order, as JTS/PostGIS expects. */
    public static Point point(double latitude, double longitude) {
        Point point = GEOMETRY_FACTORY.createPoint(new Coordinate(longitude, latitude));
        point.setSRID(SRID);
        return point;
    }

    public static double latitudeOf(Point point) {
        return point.getY();
    }

    public static double longitudeOf(Point point) {
        return point.getX();
    }

    /**
     * Haversine great-circle distance in kilometers between two lat/lng pairs.
     * Used for in-memory distance calculations (e.g. formatting API responses)
     * where a database round-trip isn't warranted; spatial filtering itself is
     * done server-side in PostGIS via ST_DWithin for index usage.
     */
    public static double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        final double earthRadiusKm = 6371.0088;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return earthRadiusKm * c;
    }
}
