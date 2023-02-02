package org.openstreetmap.josm.plugins.Splinex;

class SplineHit {
    Spline.SNode splineNodeA;
    Spline.SNode splineNodeB;
    double time = Double.NaN;

    public SplineHit(Spline.SNode splineNodeA, Spline.SNode splineNodeB) {
        this.splineNodeA = splineNodeA;
        this.splineNodeB = splineNodeB;
    }
}
