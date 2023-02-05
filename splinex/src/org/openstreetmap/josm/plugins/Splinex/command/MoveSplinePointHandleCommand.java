package org.openstreetmap.josm.plugins.Splinex.command;

import org.openstreetmap.josm.data.coor.EastNorth;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.plugins.Splinex.PointHandle;

import java.awt.event.MouseEvent;
import java.util.Collection;

import static org.openstreetmap.josm.tools.I18n.tr;

public class MoveSplinePointHandleCommand extends EditSplineCommand implements DragCommand {

    protected PointHandle pointHandle;
    protected boolean lockCounterpart;
    protected boolean lockCounterpartLength;

    public MoveSplinePointHandleCommand(PointHandle pointHandle) {
        super(pointHandle.sn);
        this.pointHandle = pointHandle;
        lockCounterpart =
            // TODO handle turnover at north
            Math.abs(pointHandle.sn.cprev.heading(EastNorth.ZERO) - EastNorth.ZERO.heading(pointHandle.sn.cnext)) < 5/180.0*Math.PI;
        lockCounterpartLength =
            Math.min(pointHandle.sn.cprev.length(), pointHandle.sn.cnext.length()) /
                Math.max(pointHandle.sn.cprev.length(), pointHandle.sn.cnext.length()) > 0.95;
    }

    @Override
    public void dragTo(EastNorth en, MouseEvent e) {
        pointHandle.movePoint(en);
        if (lockCounterpart && !e.isControlDown()) {
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
