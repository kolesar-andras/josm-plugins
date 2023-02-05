package org.openstreetmap.josm.plugins.Splinex;

import org.openstreetmap.josm.data.coor.EastNorth;

import java.util.Objects;

public class PointHandle {
    private final Spline spline;
    public final int idx;
    public final SplineNode sn;
    public final Role role;

    public PointHandle(Spline spline, int idx, Role role) {
        this.spline = spline;
        if (role == null)
            throw new IllegalArgumentException("Invalid role passed for PointHandle constructor");
        this.idx = idx;
        this.sn = spline.nodes.get(idx);
        this.role = role;
    }

    public PointHandle otherPoint(Role role) {
        return new PointHandle(spline, idx, role);
    }

    public Spline getSpline() {
        return spline;
    }

    public EastNorth getPoint() {
        EastNorth en = sn.node.getEastNorth();
        switch (role) {
            case NODE:
                return en;
            case CONTROL_PREV:
                return en.add(sn.cprev);
            case CONTROL_NEXT:
                return en.add(sn.cnext);
        }
        throw new AssertionError();
    }

    public void movePoint(EastNorth en) {
        switch (role) {
            case NODE:
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
        if (role == Role.CONTROL_NEXT) {
            sn.cprev = computeCounterpart(sn.cprev, sn.cnext, lockLength);
        } else if (role == Role.CONTROL_PREV) {
            sn.cnext = computeCounterpart(sn.cnext, sn.cprev, lockLength);
        }
    }

    public static EastNorth computeCounterpart(EastNorth previous, EastNorth moved, boolean locklength) {
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
        return this.sn == o.sn && this.role == o.role;
    }

    @Override
    public int hashCode() {
        return Objects.hash(sn, role);
    }

    public enum Role {
        NODE, CONTROL_PREV, CONTROL_NEXT
    }
}
