package org.openstreetmap.josm.plugins.Splinex.command;

import org.openstreetmap.josm.data.coor.EastNorth;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.plugins.Splinex.PointHandle;

import java.awt.event.MouseEvent;
import java.util.Collection;

import static org.openstreetmap.josm.tools.I18n.tr;

public class MoveSplinePointHandleCommand extends EditSplineCommand implements DragCommand {
    private static final double LOCK_ANGLE_THRESHOLD = 5.0 / 180.0 * Math.PI;
    private static final double LOCK_LENGTH_THRESHOLD = 0.95;

    protected PointHandle pointHandle;
    protected boolean lockCounterpart;
    protected boolean lockCounterpartLength;

    public MoveSplinePointHandleCommand(PointHandle pointHandle, boolean disableLockCounterpart) {
        super(pointHandle.sn);
        this.pointHandle = pointHandle;
        if (disableLockCounterpart) return;
        lockCounterpart =
            // TODO handle turnover at north
            Math.abs(
                pointHandle.sn.cprev.heading(EastNorth.ZERO) - EastNorth.ZERO.heading(pointHandle.sn.cnext)) < LOCK_ANGLE_THRESHOLD;
        double min = Math.min(pointHandle.sn.cprev.length(), pointHandle.sn.cnext.length());
        double max = Math.max(pointHandle.sn.cprev.length(), pointHandle.sn.cnext.length());
        lockCounterpartLength = (max == 0.0) || (min / max > LOCK_LENGTH_THRESHOLD);
    }

    public MoveSplinePointHandleCommand(PointHandle pointHandle) {
        this(pointHandle, false);
    }

    @Override
    public void dragTo(EastNorth en, MouseEvent e) {
        pointHandle.movePoint(en);
        if (lockCounterpart || e.isShiftDown()) {
            pointHandle.moveCounterpart(lockCounterpartLength);
        }
    }

    @Override
    public void fillModifiedData(Collection<OsmPrimitive> modified, Collection<OsmPrimitive> deleted, Collection<OsmPrimitive> added) {
    }

    @Override
    public String getDescriptionText() {
        return tr("Move spline node handle");
    }
}
