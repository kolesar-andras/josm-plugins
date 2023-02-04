package org.openstreetmap.josm.plugins.Splinex;

import org.openstreetmap.josm.data.coor.EastNorth;

public class BezierDef {
    public EastNorth pointA;
    public EastNorth ctrlA;
    public EastNorth ctrlB;
    public EastNorth pointB;

    public BezierDef(SplineHit splineHit) {
        pointA = splineHit.splineNodeA.node.getEastNorth();
        pointB = splineHit.splineNodeB.node.getEastNorth();
        ctrlA = splineHit.splineNodeA.cnext.add(pointA);
        ctrlB = splineHit.splineNodeB.cprev.add(pointB);
    }

    public BezierDef(EastNorth pointA, EastNorth ctrlA, EastNorth ctrlB, EastNorth pointB) {
        this.pointA = pointA;
        this.ctrlA = ctrlA;
        this.ctrlB = ctrlB;
        this.pointB = pointB;
    }
}
