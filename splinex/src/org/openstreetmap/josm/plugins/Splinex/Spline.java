// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.Splinex;

import static org.openstreetmap.josm.plugins.Splinex.SplinexPlugin.EPSILON;
import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.geom.Point2D;
import java.util.*;
import java.util.List;

import javax.swing.JOptionPane;

import org.openstreetmap.josm.command.AddCommand;
import org.openstreetmap.josm.command.Command;
import org.openstreetmap.josm.data.UndoRedoHandler;
import org.openstreetmap.josm.data.coor.EastNorth;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.data.preferences.IntegerProperty;
import org.openstreetmap.josm.data.projection.ProjectionRegistry;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.MapView;
import org.openstreetmap.josm.gui.NavigatableComponent;
import org.openstreetmap.josm.plugins.Splinex.DrawSplineAction.Direction;
import org.openstreetmap.josm.plugins.Splinex.algorithm.SplineHitCheck;
import org.openstreetmap.josm.plugins.Splinex.algorithm.Split;
import org.openstreetmap.josm.plugins.Splinex.command.*;
import org.openstreetmap.josm.plugins.Splinex.exporter.CubicBezier;

public class Spline {
    public static IntegerProperty PROP_SPLINEPOINTS = new IntegerProperty("edit.spline.num_points", 10);

    public NodeList nodes = new NodeList();

    public SplineNode getFirstSegment() {
        if (nodes.isEmpty())
            return null;
        return nodes.getFirst();
    }

    public SplineNode getLastSegment() {
        if (nodes.isEmpty())
            return null;
        return nodes.getLast();
    }

    public boolean isEmpty() {
        return nodes.isEmpty();
    }

    public int nodeCount() {
        return nodes.size();
    }

    public boolean isClosed() {
        return nodes.isClosed();
    }

    public PointHandle getNearestPointHandle(MapView mv, Point2D point) {
        PointHandle bestPH = null;
        double bestDistSq = NavigatableComponent.PROP_SNAP_DISTANCE.get();
        bestDistSq = bestDistSq * bestDistSq;
        for (int i = 0; i < nodes.size(); i++) {
            for (PointHandle.Role sp : PointHandle.Role.values()) {
                PointHandle ph = new PointHandle(this, i, sp);
                double distSq = point.distanceSq(mv.getPoint2D(ph.getPoint()));
                if (distSq < bestDistSq) {
                    bestPH = ph;
                    bestDistSq = distSq;
                }
            }
        }
        return bestPH;
    }

    SplineHitCheck splineHitCheck = new SplineHitCheck();

    public boolean doesHit(double x, double y, MapView mv) {
        return findHit(x, y, mv).isPresent();
    }

    public Optional<SplineHit> findHit(double x, double y, MapView mv) {
        //long start = System.nanoTime();
        //sht.chkCnt = 0;
        splineHitCheck.setCoord(x, y, NavigatableComponent.PROP_SNAP_DISTANCE.get());
        SplineNode prevSplineNode = null;
        Point2D prev = null;
        Point2D cbPrev = null;
        int index = 0;
        for (SplineNode sn : nodes) {
            Point2D pt = mv.getPoint2D(sn.node);
            EastNorth en = sn.node.getEastNorth();
            Point2D ca = mv.getPoint2D(en.add(sn.cprev));

            if (cbPrev != null)
                if (splineHitCheck.checkCurve(prev.getX(), prev.getY(), cbPrev.getX(), cbPrev.getY(), ca.getX(), ca.getY(),
                        pt.getX(), pt.getY()))
                    return Optional.of(new SplineHit(prevSplineNode, sn, index));
            cbPrev = mv.getPoint2D(en.add(sn.cnext));
            prev = pt;
            prevSplineNode = sn;
            index++;
        }
        //chkTime = (int) ((System.nanoTime() - start) / 1000);
        return Optional.empty();
    }

    public void finish() {
        if (nodes.isEmpty())
            return;
        int detail = PROP_SPLINEPOINTS.get();
        Way way = new Way();
        List<Command> cmds = new LinkedList<>();
        Iterator<SplineNode> it = nodes.iterator();
        SplineNode sn = it.next();
        if (sn.node.isDeleted())
            cmds.add(new UndeleteNodeCommand(sn.node));
        way.addNode(sn.node);
        EastNorth a = sn.node.getEastNorth();
        EastNorth ca = a.add(sn.cnext);
        DataSet ds = MainApplication.getLayerManager().getEditDataSet();
        while (it.hasNext()) {
            sn = it.next();
            if (sn.node.isDeleted() && sn != nodes.getFirst())
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
            UndoRedoHandler.getInstance().add(new FinishSplineCommand(this, cmds));
        }
    }

    public boolean isCloseable(PointHandle pointHandle, Direction direction) {
        return !isClosed() &&
            nodeCount() > 1 &&
            pointHandle != null && pointHandle.role == PointHandle.Role.NODE && (
                (pointHandle.idx == 0 && direction == Direction.FORWARD) ||
                (pointHandle.idx == nodeCount() - 1 && direction == Direction.BACKWARD)
            );
    }

    public void close() {
        UndoRedoHandler.getInstance().add(new CloseSplineCommand(this));
    }

    public List<OsmPrimitive> getNodes() {
        ArrayList<OsmPrimitive> result = new ArrayList<>(nodes.size());
        for (SplineNode sn : nodes) {
            result.add(sn.node);
        }
        return result;
    }

    void insertNode(SplineHit splineHit) {
        List<Command> cmds = new LinkedList<>();
        Split.Result result = Split.split(splineHit);
        Node node = new Node(result.a.pointB);
        SplineNode splineNode = new SplineNode(
            node,
            result.a.ctrlB.subtract(result.a.pointB),
            result.b.ctrlA.subtract(result.b.pointA)
        );
        cmds.add(new AddSplineNodeCommand(this, splineNode, false, splineHit.index));
        cmds.add(new EditSplineCommand(splineHit.splineNodeA));
        cmds.add(new EditSplineCommand(splineHit.splineNodeB));
        UndoRedoHandler.getInstance().add(new InsertSplineNodeCommand(cmds));
        splineHit.splineNodeA.cnext = result.a.ctrlA.subtract(result.a.pointA);
        splineHit.splineNodeB.cprev = result.b.ctrlB.subtract(result.b.pointB);
    }

}
