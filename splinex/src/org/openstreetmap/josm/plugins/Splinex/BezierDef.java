package org.openstreetmap.josm.plugins.Splinex;

import org.openstreetmap.josm.data.coor.EastNorth;

class BezierDef {
    EastNorth pointA;
    EastNorth ctrlA;
    EastNorth ctrlB;
    EastNorth pointB;

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
