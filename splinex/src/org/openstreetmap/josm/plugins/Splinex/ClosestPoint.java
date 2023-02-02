package org.openstreetmap.josm.plugins.Splinex;

import org.openstreetmap.josm.data.coor.EastNorth;
import org.openstreetmap.josm.gui.MapView;

import java.util.Optional;

import static java.lang.Math.max;
import static java.lang.Math.min;

public class ClosestPoint {

    protected final static int SAMPLES = 25;
    protected final static double EPSILON = 10e-4;

    static class BezierDef {
        EastNorth pointA;
        EastNorth pointB;
        EastNorth ctrlA;
        EastNorth ctrlB;

        public BezierDef(Spline.SplineHit splineHit) {
            pointA = splineHit.splineNodeA.node.getEastNorth();
            pointB = splineHit.splineNodeB.node.getEastNorth();
            ctrlA = splineHit.splineNodeA.cnext.add(pointA);
            ctrlB = splineHit.splineNodeB.cprev.add(pointB);
        }
    }

    public static Optional<Spline.SplineHit> findTime(int x, int y, Spline spline, MapView mv) {
        Optional<Spline.SplineHit> splineHit = spline.findHit(x, y, mv);
        if (!splineHit.isPresent()) return splineHit;
        splineHit.get().time = findTime(mv.getEastNorth(x, y), new BezierDef(splineHit.get()));
        return splineHit;
    }

    public static double findTime(EastNorth point, BezierDef bezierDef) {
        int indexOfMinimum = -1;
        double minimumDistance = Double.MAX_VALUE;
        for (int i = SAMPLES+1; i>0; i--) {
            double squaredDistance = squaredDistance(point, bezierDef, (double) i / SAMPLES);
            if (squaredDistance < minimumDistance) {
                minimumDistance = squaredDistance;
                indexOfMinimum = i;
            }
        }
        return localMinimum(
            max((double) (indexOfMinimum-1) / SAMPLES, 0),
            min((double) (indexOfMinimum+1) / SAMPLES, 1),
            point,
            bezierDef
        );
    }

    protected static double squaredDistance(EastNorth point, BezierDef bezierDef, double t) {
        return point.distanceSq(Spline.cubicBezier(
            bezierDef.pointA,
            bezierDef.ctrlA,
            bezierDef.ctrlB,
            bezierDef.pointB,
            t
        ));
    }

    protected static double localMinimum(double min, double max, EastNorth point, BezierDef bezierDef) {
        double t = Double.NaN;
        while (max - min > EPSILON) {
            t = (max + min) / 2;
            if (squaredDistance(point, bezierDef, t - EPSILON) < squaredDistance(point, bezierDef, t + EPSILON)) {
                max = t;
            } else {
                min = t;
            }
        }
        return t;
    }

}
