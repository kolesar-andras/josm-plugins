package org.openstreetmap.josm.plugins.Splinex;

public class SplineHit {
    public static final int NONE = -1;

    public SplineNode splineNodeA;
    public SplineNode splineNodeB;
    public double time = Double.NaN;
    public int index = NONE;

    public SplineHit(SplineNode splineNodeA, SplineNode splineNodeB) {
        this.splineNodeA = splineNodeA;
        this.splineNodeB = splineNodeB;
    }

    public SplineHit(SplineNode splineNodeA, SplineNode splineNodeB, int index) {
        this(splineNodeA, splineNodeB);
        this.index = index;
    }
}
