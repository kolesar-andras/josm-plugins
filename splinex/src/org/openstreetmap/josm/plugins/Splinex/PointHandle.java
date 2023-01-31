package org.openstreetmap.josm.plugins.Splinex;

import org.openstreetmap.josm.data.coor.EastNorth;

import java.util.Objects;

public class PointHandle {
    private final Spline spline;
    public final int idx;
    public final Spline.SNode sn;
    public final Spline.SplinePoint point;

    public PointHandle(Spline spline, int idx, Spline.SplinePoint point) {
        this.spline = spline;
        if (point == null)
            throw new IllegalArgumentException("Invalid SegmentPoint passed for PointHandle contructor");
        this.idx = idx;
        this.sn = spline.nodes.get(idx);
        this.point = point;
    }

    public PointHandle otherPoint(Spline.SplinePoint point) {
        return new PointHandle(spline, idx, point);
    }

    public Spline getSpline() {
        return spline;
    }

    public EastNorth getPoint() {
        EastNorth en = sn.node.getEastNorth();
        switch (point) {
            case ENDPOINT:
                return en;
            case CONTROL_PREV:
                return en.add(sn.cprev);
            case CONTROL_NEXT:
                return en.add(sn.cnext);
        }
        throw new AssertionError();
    }

    public void movePoint(EastNorth en) {
        switch (point) {
            case ENDPOINT:
                sn.node.setEastNorth(en);
                return;
            case CONTROL_PREV:
                sn.cprev = en.subtract(sn.node.getEastNorth());
                return;
            case CONTROL_NEXT:
                sn.cnext = en.subtract(sn.node.getEastNorth());
                return;
        }
        throw new AssertionError();
    }

    public void moveCounterpart(boolean lockLength) {
        if (point == Spline.SplinePoint.CONTROL_NEXT) {
            sn.cprev = computeCounterpart(sn.cprev, sn.cnext, lockLength);
        } else if (point == Spline.SplinePoint.CONTROL_PREV) {
            sn.cnext = computeCounterpart(sn.cnext, sn.cprev, lockLength);
        }
    }

    public EastNorth computeCounterpart(EastNorth previous, EastNorth moved, boolean locklength) {
        double length;
        if (locklength) {
            length = moved.length();
        } else {
            length = previous.length();
        }
        double heading = moved.heading(EastNorth.ZERO);
        return new EastNorth(length * Math.sin(heading), length * Math.cos(heading));
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof PointHandle))
            return false;
        PointHandle o = (PointHandle) other;
        return this.sn == o.sn && this.point == o.point;
    }

    @Override
    public int hashCode() {
        return Objects.hash(sn, point);
    }
}
