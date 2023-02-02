package org.openstreetmap.josm.plugins.Splinex;

import org.openstreetmap.josm.data.coor.EastNorth;

public class Split {

    static class Result {
        BezierDef a;
        BezierDef b;

        public Result(BezierDef a, BezierDef b) {
            this.a = a;
            this.b = b;
        }
    }

        public static Result split(SplineHit splineHit) {
            return split(new BezierDef(splineHit), splineHit.time);
        }

        public static Result split(BezierDef bezierDef, double t) {
        EastNorth p0 = bezierDef.pointA;
        EastNorth p1 = bezierDef.ctrlA;
        EastNorth p2 = bezierDef.ctrlB;
        EastNorth p3 = bezierDef.pointB;
        EastNorth p4 = lerp(p0, p1, t);
        EastNorth p5 = lerp(p1, p2, t);
        EastNorth p6 = lerp(p2, p3, t);
        EastNorth p7 = lerp(p4, p5, t);
        EastNorth p8 = lerp(p5, p6, t);
        EastNorth p9 = lerp(p7, p8, t);
        return new Result(
            new BezierDef(p0, p4, p7, p9),
            new BezierDef(p9, p8, p6, p3)
        );
    }

    public static EastNorth lerp(EastNorth a, EastNorth b, double t) {
        double s = 1.0 - t;
        return new EastNorth(
            a.east() * s + b.east() * t,
            a.north() * s + b.north() * t
        );
    }

}
