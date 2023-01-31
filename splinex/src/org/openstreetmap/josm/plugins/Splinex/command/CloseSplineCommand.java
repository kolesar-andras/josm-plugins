package org.openstreetmap.josm.plugins.Splinex.command;

import org.openstreetmap.josm.command.Command;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.plugins.Splinex.Spline;
import org.openstreetmap.josm.tools.ImageProvider;

import javax.swing.*;
import java.util.Collection;

public class CloseSplineCommand extends Command {

    private final Spline spline;

    public CloseSplineCommand(Spline spline) {
        super(MainApplication.getLayerManager().getEditDataSet());
        this.spline = spline;
    }

    @Override
    public boolean executeCommand() {
        spline.nodes.add(spline.nodes.get(0));
        return true;
    }

    @Override
    public void undoCommand() {
        spline.nodes.remove(spline.nodes.size() - 1);
    }

    @Override
    public void fillModifiedData(Collection<OsmPrimitive> modified, Collection<OsmPrimitive> deleted,
                                 Collection<OsmPrimitive> added) {
        // This command doesn't touches OSM data
    }

    @Override
    public String getDescriptionText() {
        return "Close spline";
    }

    @Override
    public Icon getDescriptionIcon() {
        return ImageProvider.get("aligncircle");
    }
}
