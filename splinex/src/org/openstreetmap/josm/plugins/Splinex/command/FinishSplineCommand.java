package org.openstreetmap.josm.plugins.Splinex.command;

import org.openstreetmap.josm.command.AddCommand;
import org.openstreetmap.josm.command.ChangeNodesCommand;
import org.openstreetmap.josm.command.Command;
import org.openstreetmap.josm.command.SequenceCommand;
import org.openstreetmap.josm.data.UndoRedoHandler;
import org.openstreetmap.josm.data.coor.EastNorth;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.data.projection.ProjectionRegistry;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.plugins.Splinex.Direction;
import org.openstreetmap.josm.plugins.Splinex.NodeList;
import org.openstreetmap.josm.plugins.Splinex.Spline;
import org.openstreetmap.josm.plugins.Splinex.SplineNode;
import org.openstreetmap.josm.plugins.Splinex.exporter.CubicBezier;

import javax.swing.*;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import static org.openstreetmap.josm.plugins.Splinex.SplinexPlugin.EPSILON;
import static org.openstreetmap.josm.tools.I18n.tr;

public class FinishSplineCommand extends SequenceCommand {
    private final Spline spline;
    private NodeList nodes;
    private Way way;

    public FinishSplineCommand(Spline spline, Collection<Command> sequenz) {
        super(tr("Finish spline"), sequenz);
        this.spline = spline;
    }

    @Override
    public boolean executeCommand() {
        nodes = (NodeList) spline.nodes.clone();
        way = spline.way;
        spline.nodes.clear();
        spline.way = null;
        return super.executeCommand();
    }

    @Override
    public void undoCommand() {
        super.undoCommand();
        spline.nodes = this.nodes;
        spline.way = this.way;
    }

    public static void run(Spline spline) {
        try {
            createSplineNodes(spline);
        } catch (OutsideWorldException exception) {
            JOptionPane.showMessageDialog(
                MainApplication.getMainFrame(),
                tr("Spline goes outside of the world.")
            );
        }
    }

    private static void createSplineNodes(Spline spline) {
        if (spline.nodes.isEmpty()) {
            return;
        }
        DataSet ds = MainApplication.getLayerManager().getEditDataSet();
        List<Command> cmds = new LinkedList<>();
        Way way = new Way();
        if (spline.way != null) {
            way.setNodes(spline.way.getNodes());
        }
        SplineNode lastSplineNode = null;
        Iterator<SplineNode> it = spline.nodes.iterator();
        while (it.hasNext()) {
            SplineNode splineNode = it.next();
            if (splineNode.node.isDeleted()) {
                cmds.add(new UndeleteNodeCommand(splineNode.node));
            }
            if (!way.containsNode(splineNode.node)) {
                if (!ds.containsNode(splineNode.node)) {
                    cmds.add(new AddCommand(ds, splineNode.node));
                }
                way.addNode(splineNode.node);
            }
            if (lastSplineNode != null) {
                createSplineSegmentNodes(lastSplineNode, splineNode, way, cmds, ds);
            }
            lastSplineNode = splineNode;
        }
        if (spline.way == null) {
            cmds.add(new AddCommand(ds, way));
            spline.way = way;
        }
        if (!way.isEmpty()) {
            cmds.add(new ChangeNodesCommand(ds, spline.way, way.getNodes()));
            UndoRedoHandler.getInstance().add(new FinishSplineCommand(spline, cmds));
        }
    }

    private static void createSplineSegmentNodes(SplineNode lastSplineNode, SplineNode splineNode, Way way, List<Command> cmds, DataSet ds) {
        int lastIndex = findNodeIndex(lastSplineNode.node, way);
        int index = findNodeIndex(splineNode.node, way);
        Direction direction;
        if (lastIndex < index) {
            direction = Direction.FORWARD;
        } else if (lastIndex > index) {
            direction = Direction.BACKWARD;
        } else {
            throw new RuntimeException("Node indexes in way are equal");
        }
        EastNorth a = lastSplineNode.node.getEastNorth();
        EastNorth b = splineNode.node.getEastNorth();
        EastNorth ca = a.add(lastSplineNode.cnext);
        EastNorth cb = b.add(splineNode.cprev);
        if (!a.equalsEpsilon(ca, EPSILON) || !b.equalsEpsilon(cb, EPSILON)) {
            int detail = Spline.PROP_SPLINEPOINTS.get();
            for (EastNorth eastNorth : CubicBezier.calculatePoints(a, ca, cb, b, detail)) {
                Node node = new Node(ProjectionRegistry.getProjection().eastNorth2latlon(eastNorth));
                if (node.isOutSideWorld()) {
                    throw new OutsideWorldException();
                }
                cmds.add(new AddCommand(ds, node));
                if (direction == Direction.FORWARD) {
                    lastIndex++;
                }
                way.addNode(lastIndex, node);
            }
        }
    }

    private static int findNodeIndex(Node node, Way way) {
        int count = way.getNodesCount();
        for (int i = count-1; i >= 0; i--) {
            if (way.getNode(i) == node) {
                return i;
            }
        }
        throw new IllegalArgumentException("Node not found in way");
    }

    static class OutsideWorldException extends IllegalArgumentException {}

}
