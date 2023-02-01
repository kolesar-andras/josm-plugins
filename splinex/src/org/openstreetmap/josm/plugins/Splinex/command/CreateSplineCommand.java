package org.openstreetmap.josm.plugins.Splinex.command;

import org.openstreetmap.josm.command.Command;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.plugins.Splinex.NodeList;
import org.openstreetmap.josm.plugins.Splinex.Spline;

import java.util.Collection;

import static org.openstreetmap.josm.tools.I18n.tr;

public class CreateSplineCommand extends Command {
    private final Spline spline;
    private final NodeList nodes;
    private NodeList previous;

    public CreateSplineCommand(Spline spline, NodeList nodes) {
        super(MainApplication.getLayerManager().getEditDataSet());
        this.spline = spline;
        this.nodes = nodes;
    }

    @Override
    public boolean executeCommand() {
        previous = spline.nodes;
        spline.nodes = nodes;
        return true;
    }

    @Override
    public void undoCommand() {
        spline.nodes = previous;
    }

    @Override
    public void fillModifiedData(Collection<OsmPrimitive> modified, Collection<OsmPrimitive> deleted, Collection<OsmPrimitive> added) {
        // This command doesn't touches OSM data
    }

    @Override
    public String getDescriptionText() {
        return tr("Create spline");
    }
}
