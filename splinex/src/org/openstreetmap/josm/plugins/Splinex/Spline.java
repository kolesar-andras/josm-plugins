// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.Splinex;

import static org.openstreetmap.josm.plugins.Splinex.SplinexPlugin.EPSILON;
import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.GeneralPath;
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
import org.openstreetmap.josm.plugins.Splinex.command.FinishSplineCommand;
import org.openstreetmap.josm.plugins.Splinex.command.UndeleteNodeCommand;

public class Spline {
    public static IntegerProperty PROP_SPLINEPOINTS = new IntegerProperty("edit.spline.num_points", 10);

    public static class SNode {
        public final Node node; // Endpoint
        public EastNorth cprev, cnext; // Relative offsets of control points

        public SNode(Node node) {
            this.node = node;
            cprev = EastNorth.ZERO;
            cnext = EastNorth.ZERO;
        }

        public SNode(Node node, EastNorth cprev, EastNorth cnext) {
            this.node = node;
            this.cprev = cprev;
            this.cnext = cnext;
        }
    }

    public NodeList nodes = new NodeList();

    public SNode getFirstSegment() {
        if (nodes.isEmpty())
            return null;
        return nodes.getFirst();
    }

    public SNode getLastSegment() {
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

    public void paint(Graphics2D g, MapView mv, Color curveColor, Color ctlColor, Point helperEndpoint, short direction) {
        if (nodes.isEmpty())
            return;
        final GeneralPath curv = new GeneralPath();
        final GeneralPath ctl = new GeneralPath();

        Point2D cbPrev = null;
        if (helperEndpoint != null && direction == -1) {
            cbPrev = new Point2D.Double(helperEndpoint.x, helperEndpoint.y);
            curv.moveTo(helperEndpoint.x, helperEndpoint.y);
        }
        for (SNode sn : nodes) {
            Point2D pt = mv.getPoint2D(sn.node);
            EastNorth en = sn.node.getEastNorth();

            Point2D ca = mv.getPoint2D(en.add(sn.cprev));
            Point2D cb = mv.getPoint2D(en.add(sn.cnext));

            if (cbPrev != null || !isClosed()) {
                ctl.moveTo(ca.getX(), ca.getY());
                ctl.lineTo(pt.getX(), pt.getY());
                ctl.lineTo(cb.getX(), cb.getY());
            }

            if (cbPrev == null)
                curv.moveTo(pt.getX(), pt.getY());
            else
                curv.curveTo(cbPrev.getX(), cbPrev.getY(), ca.getX(), ca.getY(), pt.getX(), pt.getY());
            cbPrev = cb;
        }
        if (helperEndpoint != null && direction == 1) {
            curv.curveTo(cbPrev.getX(), cbPrev.getY(), helperEndpoint.getX(), helperEndpoint.getY(),
                    helperEndpoint.getX(), helperEndpoint.getY());
        }
        g.setStroke(new BasicStroke(3, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(curveColor);
        g.draw(curv);
        g.setStroke(new BasicStroke(1));
        g.setColor(ctlColor);
        g.draw(ctl);
        /* if (chkTime > 0 || sht.chkCnt > 0) {
            g.drawString(tr("Check count: {0}", sht.chkCnt), 10, 60);
            g.drawString(tr("Check time: {0} us", chkTime), 10, 70);
        }
        chkTime = 0;
        sht.chkCnt = 0; */
    }

    public void paintProposedNodes(Graphics2D g, MapView mv) {
        if (nodes.isEmpty())
            return;
        double radius = 2.0;
        Stroke stroke = new BasicStroke(1.2f);
        Color color = new Color(1.0f, 1.0f, 1.0f, 0.6f);
        int detail = PROP_SPLINEPOINTS.get();
        Iterator<SNode> it = nodes.iterator();
        SNode sn = it.next();
        EastNorth a = sn.node.getEastNorth();
        EastNorth ca = a.add(sn.cnext);
        while (it.hasNext()) {
            sn = it.next();
            EastNorth b = sn.node.getEastNorth();
            EastNorth cb = b.add(sn.cprev);
            if (!a.equalsEpsilon(ca, EPSILON) || !b.equalsEpsilon(cb, EPSILON))
                for (int i = 1; i < detail; i++) {
                    Point point = mv.getPoint(cubicBezier(a, ca, cb, b, (double) i / detail));
                    Ellipse2D circle = new Ellipse2D.Double(point.x-radius/2, point.y-radius/2, radius, radius);
                    g.setStroke(stroke);
                    g.setColor(color);
                    g.draw(circle);
                }
            a = b;
            ca = a.add(sn.cnext);
        }
    }

    public enum SplinePoint {
        ENDPOINT, CONTROL_PREV, CONTROL_NEXT
    }

    public PointHandle getNearestPoint(MapView mv, Point2D point) {
        PointHandle bestPH = null;
        double bestDistSq = NavigatableComponent.PROP_SNAP_DISTANCE.get();
        bestDistSq = bestDistSq * bestDistSq;
        for (int i = 0; i < nodes.size(); i++) {
            for (SplinePoint sp : SplinePoint.values()) {
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

    SplineHitTest sht = new SplineHitTest();

    public boolean doesHit(double x, double y, MapView mv) {
        return findHit(x, y, mv).isPresent();
    }

    public Optional<SplineHit> findHit(double x, double y, MapView mv) {
        //long start = System.nanoTime();
        //sht.chkCnt = 0;
        sht.setCoord(x, y, NavigatableComponent.PROP_SNAP_DISTANCE.get());
        SNode prevSNode = null;
        Point2D prev = null;
        Point2D cbPrev = null;
        int index = 0;
        for (SNode sn : nodes) {
            Point2D pt = mv.getPoint2D(sn.node);
            EastNorth en = sn.node.getEastNorth();
            Point2D ca = mv.getPoint2D(en.add(sn.cprev));

            if (cbPrev != null)
                if (sht.checkCurve(prev.getX(), prev.getY(), cbPrev.getX(), cbPrev.getY(), ca.getX(), ca.getY(),
                        pt.getX(), pt.getY()))
                    return Optional.of(new SplineHit(prevSNode, sn, index));
            cbPrev = mv.getPoint2D(en.add(sn.cnext));
            prev = pt;
            prevSNode = sn;
            index++;
        }
        //chkTime = (int) ((System.nanoTime() - start) / 1000);
        return Optional.empty();
    }

    public void finishSpline() {
        if (nodes.isEmpty())
            return;
        int detail = PROP_SPLINEPOINTS.get();
        Way w = new Way();
        List<Command> cmds = new LinkedList<>();
        Iterator<SNode> it = nodes.iterator();
        SNode sn = it.next();
        if (sn.node.isDeleted())
            cmds.add(new UndeleteNodeCommand(sn.node));
        w.addNode(sn.node);
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
                for (int i = 1; i < detail; i++) {
                    Node n = new Node(ProjectionRegistry.getProjection().eastNorth2latlon(
                            cubicBezier(a, ca, cb, b, (double) i / detail)));
                    if (n.isOutSideWorld()) {
                        JOptionPane.showMessageDialog(MainApplication.getMainFrame(), tr("Spline goes outside of the world."));
                        return;
                    }
                    cmds.add(new AddCommand(ds, n));
                    w.addNode(n);
                }
            w.addNode(sn.node);
            a = b;
            ca = a.add(sn.cnext);
        }
        if (!cmds.isEmpty()) {
            cmds.add(new AddCommand(ds, w));
            UndoRedoHandler.getInstance().add(new FinishSplineCommand(this, cmds));
        }
    }

    /**
     * A cubic bezier method to calculate the point at t along the Bezier Curve
     * give
     */
    public static EastNorth cubicBezier(EastNorth a0, EastNorth a1, EastNorth a2, EastNorth a3, double t) {
        return new EastNorth(cubicBezierPoint(a0.getX(), a1.getX(), a2.getX(), a3.getX(), t), cubicBezierPoint(
                a0.getY(), a1.getY(), a2.getY(), a3.getY(), t));
    }

    /**
     * The cubic Bezier equation.
     */
    private static double cubicBezierPoint(double a0, double a1, double a2, double a3, double t) {
        return Math.pow(1 - t, 3) * a0 + 3 * Math.pow(1 - t, 2) * t * a1 + 3 * (1 - t) * Math.pow(t, 2) * a2
                + Math.pow(t, 3) * a3;
    }

    public List<OsmPrimitive> getNodes() {
        ArrayList<OsmPrimitive> result = new ArrayList<>(nodes.size());
        for (SNode sn : nodes) {
            result.add(sn.node);
        }
        return result;
    }

}
