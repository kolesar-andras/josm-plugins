package org.openstreetmap.josm.plugins.Splinex;

import org.openstreetmap.josm.data.coor.EastNorth;

import java.util.ArrayList;
import java.util.List;

import static java.lang.Math.PI;

public class AdaptiveBezier {
    public static int RECURSION_LIMIT = 8;
    public static double FLT_EPSILON = 1.19209290e-7;
    public static double CURVE_ANGLE_TOLERANCE_EPSILON = 0.01;
    public static double M_ANGLE_TOLERANCE = 0.0;
    public static double M_CUSP_LIMIT = 0.0;

    public static List<EastNorth> calculatePoints(EastNorth a, EastNorth ca, EastNorth cb, EastNorth b, double distanceTolerance) {
        List<EastNorth> points = new ArrayList<>();
        subdivide(a, ca, cb, b, points, distanceTolerance, 0);
        return points;
    }

    public static void subdivide(EastNorth a, EastNorth ca, EastNorth cb, EastNorth b, List<EastNorth> points, double distanceTolerance, int level) {
        double x1 = a.getX();
        double x2 = ca.getX();
        double x3 = cb.getX();
        double x4 = b.getX();

        double y1 = a.getY();
        double y2 = ca.getY();
        double y3 = cb.getY();
        double y4 = b.getY();

        subdivide(x1, y1, x2, y2, x3, y3, x4, y4, points, distanceTolerance, level);
    }

    public static void subdivide(double x1, double y1, double x2, double y2, double x3, double y3, double x4, double y4, List<EastNorth> points, double distanceTolerance, int level) {
        if (level > RECURSION_LIMIT) return;

        // Calculate all the mid-points of the line segments
        double x12   = (x1 + x2) / 2;
        double y12   = (y1 + y2) / 2;
        double x23   = (x2 + x3) / 2;
        double y23   = (y2 + y3) / 2;
        double x34   = (x3 + x4) / 2;
        double y34   = (y3 + y4) / 2;
        double x123  = (x12 + x23) / 2;
        double y123  = (y12 + y23) / 2;
        double x234  = (x23 + x34) / 2;
        double y234  = (y23 + y34) / 2;
        double x1234 = (x123 + x234) / 2;
        double y1234 = (y123 + y234) / 2;

        if (level > 0) { // Enforce subdivision first time
            // Try to approximate the full cubic curve by a single straight line
            double dx = x4-x1;
            double dy = y4-y1;

            double d2 = Math.abs((x2 - x4) * dy - (y2 - y4) * dx);
            double d3 = Math.abs((x3 - x4) * dy - (y3 - y4) * dx);

            double da1, da2;

            if (d2 > FLT_EPSILON && d3 > FLT_EPSILON) {
                // Regular care
                if ((d2 + d3)*(d2 + d3) <= distanceTolerance * (dx*dx + dy*dy)) {
                    // If the curvature doesn't exceed the distanceTolerance value
                    // we tend to finish subdivisions.
                    if (M_ANGLE_TOLERANCE < CURVE_ANGLE_TOLERANCE_EPSILON) {
                        points.add(new EastNorth(x1234, y1234));
                        return;
                    }

                    // Angle & Cusp Condition
                    double a23 = Math.atan2(y3 - y2, x3 - x2);
                    da1 = Math.abs(a23 - Math.atan2(y2 - y1, x2 - x1));
                    da2 = Math.abs(Math.atan2(y4 - y3, x4 - x3) - a23);
                    if (da1 >= PI) da1 = 2 * PI - da1;
                    if (da2 >= PI) da2 = 2 * PI - da2;

                    if (da1 + da2 < M_ANGLE_TOLERANCE) {
                        // Finally we can stop the recursion
                        points.add(new EastNorth(x1234, y1234));
                        return;
                    }

                    if (M_CUSP_LIMIT != 0.0) {
                        if (da1 > M_CUSP_LIMIT) {
                            points.add(new EastNorth(x2, y2));
                            return;
                        }

                        if (da2 > M_CUSP_LIMIT) {
                            points.add(new EastNorth(x3, y3));
                            return;
                        }
                    }
                }
            }
            else {
                if (d2 > FLT_EPSILON) {
                    // p1,p3,p4 are collinear, p2 is considerable
                    if (d2 * d2 <= distanceTolerance * (dx*dx + dy*dy)) {
                        if (M_ANGLE_TOLERANCE < CURVE_ANGLE_TOLERANCE_EPSILON) {
                            points.add(new EastNorth(x1234, y1234));
                            return;
                        }

                        // Angle Condition
                        da1 = Math.abs(Math.atan2(y3 - y2, x3 - x2) - Math.atan2(y2 - y1, x2 - x1));
                        if (da1 >= PI) da1 = 2 * PI - da1;

                        if (da1 < M_ANGLE_TOLERANCE) {
                            points.add(new EastNorth(x2, y2));
                            points.add(new EastNorth(x3, y3));
                            return;
                        }

                        if (M_CUSP_LIMIT != 0.0) {
                            if (da1 > M_CUSP_LIMIT) {
                                points.add(new EastNorth(x2, y2));
                                return;
                            }
                        }
                    }
                }
                else if (d3 > FLT_EPSILON) {
                    // p1,p2,p4 are collinear, p3 is considerable
                    if (d3 * d3 <= distanceTolerance * (dx*dx + dy*dy)) {
                        if (M_ANGLE_TOLERANCE < CURVE_ANGLE_TOLERANCE_EPSILON) {
                            points.add(new EastNorth(x1234, y1234));
                            return;
                        }

                        // Angle Condition
                        da1 = Math.abs(Math.atan2(y4 - y3, x4 - x3) - Math.atan2(y3 - y2, x3 - x2));
                        if (da1 >= PI) da1 = 2*PI - da1;

                        if (da1 < M_ANGLE_TOLERANCE) {
                            points.add(new EastNorth(x2, y2));
                            points.add(new EastNorth(x3, y3));
                            return;
                        }

                        if (M_CUSP_LIMIT != 0.0) {
                            if (da1 > M_CUSP_LIMIT)
                            {
                                points.add(new EastNorth(x3, y3));
                                return;
                            }
                        }
                    }
                }
                else {
                    // Collinear case
                    dx = x1234 - (x1 + x4) / 2;
                    dy = y1234 - (y1 + y4) / 2;
                    if (dx*dx + dy*dy <= distanceTolerance) {
                        points.add(new EastNorth(x1234, y1234));
                        return;
                    }
                }
            }
        }

        // Continue subdivision
        subdivide(x1, y1, x12, y12, x123, y123, x1234, y1234, points, distanceTolerance, level + 1);
        subdivide(x1234, y1234, x234, y234, x34, y34, x4, y4, points, distanceTolerance, level + 1);
    }

}
