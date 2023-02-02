package org.openstreetmap.josm.plugins.Splinex;

class SplineHit {
    public static final int NONE = -1;

    Spline.SNode splineNodeA;
    Spline.SNode splineNodeB;
    double time = Double.NaN;
    int index = NONE;

    public SplineHit(Spline.SNode splineNodeA, Spline.SNode splineNodeB) {
        this.splineNodeA = splineNodeA;
        this.splineNodeB = splineNodeB;
    }

    public SplineHit(Spline.SNode splineNodeA, Spline.SNode splineNodeB, int index) {
        this(splineNodeA, splineNodeB);
        this.index = index;
    }
}
