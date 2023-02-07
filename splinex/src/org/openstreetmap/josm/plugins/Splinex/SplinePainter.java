package org.openstreetmap.josm.plugins.Splinex;

import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.coor.EastNorth;
import org.openstreetmap.josm.data.osm.visitor.paint.MapPaintSettings;
import org.openstreetmap.josm.gui.MapView;
import org.openstreetmap.josm.gui.layer.MapViewPaintable;
import org.openstreetmap.josm.plugins.Splinex.exporter.CubicBezier;

import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.GeneralPath;
import java.awt.geom.Point2D;
import java.util.Iterator;

import static org.openstreetmap.josm.plugins.Splinex.Spline.PROP_SPLINEPOINTS;
import static org.openstreetmap.josm.plugins.Splinex.SplinexPlugin.EPSILON;

public class SplinePainter implements MapViewPaintable {

    private final DrawSplineAction drawSplineAction;

    public SplinePainter(DrawSplineAction drawSplineAction) {
        this.drawSplineAction = drawSplineAction;
    }

    @Override
    public void paint(Graphics2D graphics2D, MapView mapView, Bounds box) {
        Spline spline = drawSplineAction.layerListener.getSpline();
        if (spline == null) return;
        graphics2D.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        paintSpline(graphics2D, mapView, spline, drawSplineAction.rubberLineColor, Color.green, drawSplineAction.helperEndpoint, drawSplineAction.direction);
        paintProposedNodes(graphics2D, mapView, spline);
        paintPointHandle(graphics2D, mapView);
    }

    public void paintSpline(Graphics2D g, MapView mv, Spline spline, Color curveColor, Color ctlColor, Point helperEndpoint, Direction direction) {
        if (spline.nodes.isEmpty())
            return;
        final GeneralPath curv = new GeneralPath();
        final GeneralPath ctl = new GeneralPath();

        Point2D cbPrev = null;
        if (helperEndpoint != null && direction == Direction.BACKWARD) {
            cbPrev = new Point2D.Double(helperEndpoint.x, helperEndpoint.y);
            curv.moveTo(helperEndpoint.x, helperEndpoint.y);
        }
        for (SplineNode sn : spline.nodes) {
            Point2D pt = mv.getPoint2D(sn.node);
            EastNorth en = sn.node.getEastNorth();

            Point2D ca = mv.getPoint2D(en.add(sn.cprev));
            Point2D cb = mv.getPoint2D(en.add(sn.cnext));

            if (cbPrev != null || !spline.isClosed()) {
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
        if (helperEndpoint != null && direction == Direction.FORWARD) {
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

    public void paintProposedNodes(Graphics2D g, MapView mv, Spline spline) {
        if (spline.nodes.isEmpty())
            return;
        double radius = 2.0;
        Stroke stroke = new BasicStroke(1.2f);
        Color color = new Color(1.0f, 1.0f, 1.0f, 0.6f);
        int detail = PROP_SPLINEPOINTS.get();
        Iterator<SplineNode> it = spline.nodes.iterator();
        SplineNode sn = it.next();
        EastNorth a = sn.node.getEastNorth();
        EastNorth ca = a.add(sn.cnext);
        while (it.hasNext()) {
            sn = it.next();
            EastNorth b = sn.node.getEastNorth();
            EastNorth cb = b.add(sn.cprev);
            if (!a.equalsEpsilon(ca, EPSILON) || !b.equalsEpsilon(cb, EPSILON))
                for (EastNorth eastNorth : CubicBezier.calculatePoints(a, ca, cb, b, detail)) {
                    Point point = mv.getPoint(eastNorth);
                    Ellipse2D circle = new Ellipse2D.Double(point.x-radius/2, point.y-radius/2, radius, radius);
                    g.setStroke(stroke);
                    g.setColor(color);
                    g.draw(circle);
                }
            a = b;
            ca = a.add(sn.cnext);
        }
    }

    protected void paintPointHandle(Graphics2D graphics2D, MapView mapView) {
        if (drawSplineAction.pointHandle != null && (
                drawSplineAction.pointHandle.role != PointHandle.Role.NODE ||
                drawSplineAction.nodeHighlight.isDeleted()
        )) {
            graphics2D.setColor(MapPaintSettings.INSTANCE.getSelectedColor());
            Point point = mapView.getPoint(drawSplineAction.pointHandle.getPoint());
            graphics2D.fillRect(point.x - 1, point.y - 1, 3, 3);
        }
    }

}
