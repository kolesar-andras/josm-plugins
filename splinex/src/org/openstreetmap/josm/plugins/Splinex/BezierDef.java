package org.openstreetmap.josm.plugins.Splinex;

import org.openstreetmap.josm.data.coor.EastNorth;

class BezierDef {
    EastNorth pointA;
    EastNorth pointB;
    EastNorth ctrlA;
    EastNorth ctrlB;

    public BezierDef(SplineHit splineHit) {
        pointA = splineHit.splineNodeA.node.getEastNorth();
        pointB = splineHit.splineNodeB.node.getEastNorth();
        ctrlA = splineHit.splineNodeA.cnext.add(pointA);
        ctrlB = splineHit.splineNodeB.cprev.add(pointB);
    }
}
