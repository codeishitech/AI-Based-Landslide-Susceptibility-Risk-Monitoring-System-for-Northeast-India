package com.ner.landslide.util;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;

/**
 * H2 User-Defined Spatial Functions for PostGIS query compatibility when running with H2 database.
 */
public class H2SpatialFunctions {

    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory(new PrecisionModel(), 4326);

    public static Geometry makePoint(double x, double y) {
        Point point = GEOMETRY_FACTORY.createPoint(new Coordinate(x, y));
        point.setSRID(4326);
        return point;
    }

    public static Geometry setSRID(Geometry geom, int srid) {
        if (geom != null) {
            geom.setSRID(srid);
        }
        return geom;
    }

    public static boolean dWithin(Geometry g1, Geometry g2, double distance) {
        if (g1 == null || g2 == null) return false;
        return g1.distance(g2) <= distance;
    }

    public static double distance(Geometry g1, Geometry g2) {
        if (g1 == null || g2 == null) return 0.0;
        return g1.distance(g2);
    }

    public static boolean contains(Geometry g1, Geometry g2) {
        if (g1 == null || g2 == null) return false;
        return g1.contains(g2);
    }
}
