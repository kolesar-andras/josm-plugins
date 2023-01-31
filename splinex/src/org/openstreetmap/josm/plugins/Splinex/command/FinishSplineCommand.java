package org.openstreetmap.josm.plugins.Splinex.command;

import org.openstreetmap.josm.command.Command;
import org.openstreetmap.josm.command.SequenceCommand;
import org.openstreetmap.josm.plugins.Splinex.Spline;

import java.util.Arrays;
import java.util.Collection;

import static org.openstreetmap.josm.tools.I18n.tr;

public class FinishSplineCommand extends SequenceCommand {
    private final Spline spline;
    public Spline.SNode[] saveSegments;

    public FinishSplineCommand(Spline spline, Collection<Command> sequenz) {
        super(tr("Finish spline"), sequenz);
        this.spline = spline;
    }

    @Override
    public boolean executeCommand() {
        saveSegments = new Spline.SNode[spline.nodes.size()];
        int i = 0;
        for (Spline.SNode sn : spline.nodes) {
            saveSegments[i++] = sn;
        }
        spline.nodes.clear();
        return super.executeCommand();
    }

    @Override
    public void undoCommand() {
        super.undoCommand();
        spline.nodes.clear();
        spline.nodes.addAll(Arrays.asList(saveSegments));
    }
}
