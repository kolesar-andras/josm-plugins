package org.openstreetmap.josm.plugins.Splinex.command;

import org.openstreetmap.josm.command.Command;
import org.openstreetmap.josm.command.SequenceCommand;
import org.openstreetmap.josm.data.coor.EastNorth;
import org.openstreetmap.josm.plugins.Splinex.PointHandle;
import org.openstreetmap.josm.plugins.Splinex.SplineHit;

import java.awt.event.MouseEvent;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import static java.lang.Math.pow;
import static org.openstreetmap.josm.tools.I18n.tr;

public class DragSplineCommand extends SequenceCommand implements DragCommand {
    public EastNorth dragReference;
    protected SplineHit splineHit;

    public DragSplineCommand(Collection<Command> sequenz, EastNorth dragReference, SplineHit splineHit) {
        super(tr("Drag spline"), sequenz);
        this.dragReference = dragReference;
        this.splineHit = splineHit;
    }

    @Override
    public boolean executeCommand() {
        return super.executeCommand();
    }

    @Override
    public void undoCommand() {
        super.undoCommand();
    }

    @Override
    public void dragTo(EastNorth en, MouseEvent e) {
        EastNorth delta = delta(en);
        double t = splineHit.time;
        double weight;
        if (t <= 1.0 / 6.0) {
            weight = 0;
        } else if (t <= 0.5) {
            weight = pow((6 * t - 1) / 2.0, 3) / 2;
        } else if (t <= 5.0 / 6.0) {
            weight = (1 - pow((6 * (1 - t) - 1) / 2.0, 3)) / 2 + 0.5;
        } else {
            weight = 1;
        }
        double scale0 = (1 - weight) / (3 * t * (1 - t) * (1 - t));
        double scale1 = weight / (3 * t * t * (1 - t));
        EastNorth offset0 = delta.scale(scale0);
        EastNorth offset1 = delta.scale(scale1);
        if (e.isAltDown()) {
            splineHit.splineNodeA.cnext = splineHit.splineNodeA.cnext.scale(
                splineHit.splineNodeA.cnext.add(offset0).length() /
                    splineHit.splineNodeA.cnext.length()
            );
            splineHit.splineNodeB.cprev = splineHit.splineNodeB.cprev.scale(
                splineHit.splineNodeB.cprev.add(offset1).length() /
                    splineHit.splineNodeB.cprev.length()
            );
        } else {
            splineHit.splineNodeA.cnext = splineHit.splineNodeA.cnext.add(offset0);
            splineHit.splineNodeB.cprev = splineHit.splineNodeB.cprev.add(offset1);
            splineHit.splineNodeA.cprev = PointHandle.computeCounterpart(splineHit.splineNodeA.cprev, splineHit.splineNodeA.cnext, false);
            splineHit.splineNodeB.cnext = PointHandle.computeCounterpart(splineHit.splineNodeB.cnext, splineHit.splineNodeB.cprev, false);
        }
    }

    public static DragSplineCommand create(SplineHit splineHit, EastNorth dragReference) {
        List<Command> cmds = new LinkedList<>();
        cmds.add(new EditSplineCommand(splineHit.splineNodeA));
        cmds.add(new EditSplineCommand(splineHit.splineNodeB));
        return new DragSplineCommand(cmds, dragReference, splineHit);
    }

    public EastNorth delta(EastNorth draggedTo) {
        EastNorth delta = draggedTo.subtract(dragReference);
        dragReference = draggedTo;
        return delta;
    }

}
