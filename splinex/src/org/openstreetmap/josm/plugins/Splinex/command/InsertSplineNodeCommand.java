package org.openstreetmap.josm.plugins.Splinex.command;

import org.openstreetmap.josm.command.Command;
import org.openstreetmap.josm.command.SequenceCommand;
import org.openstreetmap.josm.plugins.Splinex.Spline;

import java.util.Collection;

import static org.openstreetmap.josm.tools.I18n.tr;

public class InsertSplineNodeCommand extends SequenceCommand {
    private final Spline spline;

    public InsertSplineNodeCommand(Spline spline, Collection<Command> sequenz) {
        super(tr("Insert node to spline"), sequenz);
        this.spline = spline;
    }

    @Override
    public boolean executeCommand() {
        return super.executeCommand();
    }

    @Override
    public void undoCommand() {
        super.undoCommand();
    }
}
