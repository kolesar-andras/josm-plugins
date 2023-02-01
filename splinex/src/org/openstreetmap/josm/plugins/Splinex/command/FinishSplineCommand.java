package org.openstreetmap.josm.plugins.Splinex.command;

import org.openstreetmap.josm.command.Command;
import org.openstreetmap.josm.command.SequenceCommand;
import org.openstreetmap.josm.plugins.Splinex.NodeList;
import org.openstreetmap.josm.plugins.Splinex.Spline;

import java.util.Collection;

import static org.openstreetmap.josm.tools.I18n.tr;

public class FinishSplineCommand extends SequenceCommand {
    private final Spline spline;
    public NodeList nodes;

    public FinishSplineCommand(Spline spline, Collection<Command> sequenz) {
        super(tr("Finish spline"), sequenz);
        this.spline = spline;
    }

    @Override
    public boolean executeCommand() {
        this.nodes = (NodeList) spline.nodes.clone();
        spline.nodes.clear();
        return super.executeCommand();
    }

    @Override
    public void undoCommand() {
        super.undoCommand();
        spline.nodes = this.nodes;
    }
}
