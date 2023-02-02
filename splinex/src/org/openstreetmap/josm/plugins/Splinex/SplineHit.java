package org.openstreetmap.josm.plugins.Splinex;

public class SplineHit {
    public static final int NONE = -1;

    public Spline.SNode splineNodeA;
    public Spline.SNode splineNodeB;
    public double time = Double.NaN;
    public int index = NONE;

    public SplineHit(Spline.SNode splineNodeA, Spline.SNode splineNodeB) {
        this.splineNodeA = splineNodeA;
        this.splineNodeB = splineNodeB;
    }

    public SplineHit(Spline.SNode splineNodeA, Spline.SNode splineNodeB, int index) {
        this(splineNodeA, splineNodeB);
        this.index = index;
    }
}
