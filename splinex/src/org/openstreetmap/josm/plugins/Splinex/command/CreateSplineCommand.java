package org.openstreetmap.josm.plugins.Splinex.command;

import org.openstreetmap.josm.command.Command;
import org.openstreetmap.josm.data.osm.DataSet;
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
    private boolean existing;

    public CreateSplineCommand(Spline spline, NodeList nodes, boolean existing) {
        super(MainApplication.getLayerManager().getEditDataSet());
        this.spline = spline;
        this.nodes = nodes;
        this.existing = existing;
    }

    @Override
    public boolean executeCommand() {
        previous = spline.nodes;
        spline.nodes = nodes;
        if (!existing) {
            DataSet dataset = getAffectedDataSet();
            for (Spline.SNode sn : nodes) {
                if (!dataset.containsNode(sn.node)) {
                    dataset.addPrimitive(sn.node);
                }
            }
        }
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
