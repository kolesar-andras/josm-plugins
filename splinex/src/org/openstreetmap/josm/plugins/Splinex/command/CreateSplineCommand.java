package org.openstreetmap.josm.plugins.Splinex.command;

import org.openstreetmap.josm.command.AddCommand;
import org.openstreetmap.josm.command.Command;
import org.openstreetmap.josm.command.SequenceCommand;
import org.openstreetmap.josm.data.UndoRedoHandler;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.plugins.Splinex.NodeList;
import org.openstreetmap.josm.plugins.Splinex.Spline;
import org.openstreetmap.josm.plugins.Splinex.SplineNode;
import org.openstreetmap.josm.plugins.Splinex.importer.Importer;

import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

import static org.openstreetmap.josm.gui.MainApplication.getLayerManager;
import static org.openstreetmap.josm.tools.I18n.tr;

public class CreateSplineCommand extends SequenceCommand {
    private final Spline target;
    private final Spline source;

    public CreateSplineCommand(Spline target, Spline source, List<Command> cmds) {
        super(getLayerManager().getEditDataSet(), tr("Create spline"), cmds, false);
        this.target = target;
        this.source = source;
    }

    @Override
    public boolean executeCommand() {
        super.executeCommand();
        swap();
        return true;
    }

    @Override
    public void undoCommand() {
        swap();
        super.undoCommand();
    }

    private void swap() {
        NodeList nodeList = target.nodes;
        target.nodes = source.nodes;
        source.nodes = nodeList;
        Way way = target.way;
        target.way = source.way;
        source.way = way;
    }

    public static void fromSelection(Spline target, Importer importer) {
        DataSet ds = getLayerManager().getEditDataSet();
        if (ds == null) return;
        Way way = ds.getLastSelectedWay();
        if (way == null) return;
        if (way.getNodesCount() < 3) return;
        Spline spline = importer.fromNodes(way);
        List<Command> cmds = new LinkedList<>();
        DataSet dataset = getLayerManager().getEditDataSet();
        for (SplineNode sn : spline.nodes.stream().distinct().collect(Collectors.toList())) {
            if (!dataset.containsNode(sn.node)) {
                cmds.add(new AddCommand(dataset, sn.node));
            }
        }
        UndoRedoHandler.getInstance().add(new CreateSplineCommand(target, spline, cmds));
        MainApplication.getLayerManager().invalidateEditLayer();
    }

}
