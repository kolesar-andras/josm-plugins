package org.openstreetmap.josm.plugins.Splinex.exporter;

import org.openstreetmap.josm.data.coor.EastNorth;

import java.util.ArrayList;
import java.util.List;

public class CubicBezier {

    public static List<EastNorth> calculatePoints(EastNorth a, EastNorth ca, EastNorth cb, EastNorth b, int detail) {
        List<EastNorth> points = new ArrayList<>();
        for (int i = 1; i < detail; i++) {
            points.add(cubicBezier(a, ca, cb, b, (double) i / detail));
        }
        return points;
    }

    /**
     * A cubic bezier method to calculate the point at t along the Bezier Curve
     * give
     */
    public static EastNorth cubicBezier(EastNorth a0, EastNorth a1, EastNorth a2, EastNorth a3, double t) {
        return new EastNorth(cubicBezierPoint(a0.getX(), a1.getX(), a2.getX(), a3.getX(), t), cubicBezierPoint(
            a0.getY(), a1.getY(), a2.getY(), a3.getY(), t));
    }

    /**
     * The cubic Bezier equation.
     */
    public static double cubicBezierPoint(double a0, double a1, double a2, double a3, double t) {
        return Math.pow(1 - t, 3) * a0 + 3 * Math.pow(1 - t, 2) * t * a1 + 3 * (1 - t) * Math.pow(t, 2) * a2
            + Math.pow(t, 3) * a3;
    }

}
