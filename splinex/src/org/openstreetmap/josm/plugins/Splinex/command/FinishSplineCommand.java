package org.openstreetmap.josm.plugins.Splinex.command;

import org.openstreetmap.josm.command.AddCommand;
import org.openstreetmap.josm.command.Command;
import org.openstreetmap.josm.command.SequenceCommand;
import org.openstreetmap.josm.data.UndoRedoHandler;
import org.openstreetmap.josm.data.coor.EastNorth;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.data.projection.ProjectionRegistry;
import org.openstreetmap.josm.gui.MainApplication;
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

    public static void run(Spline spline) {
        if (spline.nodes.isEmpty())
            return;
        int detail = Spline.PROP_SPLINEPOINTS.get();
        Way way = new Way();
        List<Command> cmds = new LinkedList<>();
        Iterator<SplineNode> it = spline.nodes.iterator();
        SplineNode sn = it.next();
        if (sn.node.isDeleted())
            cmds.add(new UndeleteNodeCommand(sn.node));
        way.addNode(sn.node);
        EastNorth a = sn.node.getEastNorth();
        EastNorth ca = a.add(sn.cnext);
        DataSet ds = MainApplication.getLayerManager().getEditDataSet();
        while (it.hasNext()) {
            sn = it.next();
            if (sn.node.isDeleted() && sn != spline.nodes.getFirst())
                cmds.add(new UndeleteNodeCommand(sn.node));
            EastNorth b = sn.node.getEastNorth();
            EastNorth cb = b.add(sn.cprev);
            if (!a.equalsEpsilon(ca, EPSILON) || !b.equalsEpsilon(cb, EPSILON))
                for (EastNorth eastNorth : CubicBezier.calculatePoints(a, ca, cb, b, detail)) {
                    Node node = new Node(ProjectionRegistry.getProjection().eastNorth2latlon(eastNorth));
                    if (node.isOutSideWorld()) {
                        JOptionPane.showMessageDialog(MainApplication.getMainFrame(), tr("Spline goes outside of the world."));
                        return;
                    }
                    cmds.add(new AddCommand(ds, node));
                    way.addNode(node);
                }
            way.addNode(sn.node);
            a = b;
            ca = a.add(sn.cnext);
        }
        if (!way.isEmpty()) {
            cmds.add(new AddCommand(ds, way));
            UndoRedoHandler.getInstance().add(new FinishSplineCommand(spline, cmds));
        }
    }

}
