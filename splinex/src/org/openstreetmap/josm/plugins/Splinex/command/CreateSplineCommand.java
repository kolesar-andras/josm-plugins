package org.openstreetmap.josm.plugins.Splinex.command;

import org.openstreetmap.josm.command.Command;
import org.openstreetmap.josm.data.UndoRedoHandler;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.plugins.Splinex.NodeList;
import org.openstreetmap.josm.plugins.Splinex.Spline;
import org.openstreetmap.josm.plugins.Splinex.SplineNode;
import org.openstreetmap.josm.plugins.Splinex.importer.SchneiderImporter;

import java.util.Collection;

import static org.openstreetmap.josm.gui.MainApplication.getLayerManager;
import static org.openstreetmap.josm.tools.I18n.tr;

public class CreateSplineCommand extends Command {
    private final Spline spline;
    private final NodeList nodes;
    private NodeList previous;

    public CreateSplineCommand(Spline spline, NodeList nodes) {
        super(getLayerManager().getEditDataSet());
        this.spline = spline;
        this.nodes = nodes;
    }

    @Override
    public boolean executeCommand() {
        previous = spline.nodes;
        spline.nodes = nodes;
        DataSet dataset = getAffectedDataSet();
        for (SplineNode sn : nodes) {
            if (!dataset.containsNode(sn.node)) {
                dataset.addPrimitive(sn.node);
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

    public static void fromSelection(Spline target) {
        DataSet ds = getLayerManager().getEditDataSet();
        if (ds == null) return;
        Way way = ds.getLastSelectedWay();
        if (way == null) return;
        if (way.getNodesCount() < 3) return;
        Spline spline = SchneiderImporter.fromNodes(way.getNodes(), 0.5, way.isClosed());
        UndoRedoHandler.getInstance().add(new CreateSplineCommand(target, spline.nodes));
    }

}
