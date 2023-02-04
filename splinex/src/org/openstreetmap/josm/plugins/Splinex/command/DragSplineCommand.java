package org.openstreetmap.josm.plugins.Splinex.command;

import org.openstreetmap.josm.command.Command;
import org.openstreetmap.josm.command.SequenceCommand;
import org.openstreetmap.josm.data.UndoRedoHandler;
import org.openstreetmap.josm.data.coor.EastNorth;
import org.openstreetmap.josm.plugins.Splinex.SplineHit;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import static org.openstreetmap.josm.tools.I18n.tr;

public class DragSplineCommand extends SequenceCommand {
    public EastNorth dragReference;

    public DragSplineCommand(Collection<Command> sequenz, EastNorth dragReference) {
        super(tr("Drag spline"), sequenz);
        this.dragReference = dragReference;
    }

    @Override
    public boolean executeCommand() {
        return super.executeCommand();
    }

    @Override
    public void undoCommand() {
        super.undoCommand();
    }

    public static void create(SplineHit splineHit, EastNorth dragReference) {
        List<Command> cmds = new LinkedList<>();
        cmds.add(new EditSplineCommand(splineHit.splineNodeA));
        cmds.add(new EditSplineCommand(splineHit.splineNodeB));
        UndoRedoHandler.getInstance().add(new DragSplineCommand(cmds, dragReference));
    }

    public EastNorth delta(EastNorth draggedTo) {
        EastNorth delta = draggedTo.subtract(dragReference);
        dragReference = draggedTo;
        return delta;
    }
}
