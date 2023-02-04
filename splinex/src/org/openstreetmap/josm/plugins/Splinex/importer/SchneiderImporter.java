package org.openstreetmap.josm.plugins.Splinex.importer;

import org.jhotdraw.geom.Bezier;
import org.jhotdraw.geom.BezierPath;
import org.openstreetmap.josm.data.coor.EastNorth;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.plugins.Splinex.Spline;
import org.openstreetmap.josm.plugins.Splinex.SplineNode;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;

public class SchneiderImporter {
    public static Spline fromNodes(List<Node> nodes, double smooth, boolean closed) {
        Spline spline = new Spline();
        List<Point2D.Double> points = new ArrayList<>();
        for (Node node : nodes) {
            EastNorth eastNorth = node.getEastNorth();
            points.add(new Point2D.Double(eastNorth.getX(), eastNorth.getY()));
        }
        BezierPath path = Bezier.fitBezierPath(points, 1.0);
        for (BezierPath.Node node : path) {
            EastNorth[] c = new EastNorth[3];
            for (short i=0; i<3; i++) {
                c[i] = new EastNorth(node.x[i], node.y[i]);
            }
            spline.nodes.add(
                new SplineNode(
                    new Node(c[0]),
                    c[1].subtract(c[0]),
                    c[2].subtract(c[0])
                )
            );
        }
        if (closed) {
            spline.nodes.getFirst().cprev = spline.nodes.getLast().cprev;
            spline.nodes.remove(spline.nodes.size()-1);
            spline.nodes.close();
        }
        return spline;
    }
}
