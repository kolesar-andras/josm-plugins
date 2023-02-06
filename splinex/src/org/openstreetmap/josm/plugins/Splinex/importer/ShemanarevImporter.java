package org.openstreetmap.josm.plugins.Splinex.importer;

import org.openstreetmap.josm.data.coor.EastNorth;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.plugins.Splinex.Spline;
import org.openstreetmap.josm.plugins.Splinex.SplineNode;

import java.util.List;

import static java.lang.Math.sqrt;

public class ShemanarevImporter implements Importer {
    public double smooth = 0.5;

    public Spline fromNodes(Way way) {
        Spline spline = new Spline();
        List<Node> nodes = way.getNodes();
        int size = nodes.size();
        int last;
        if (way.isClosed()) {
            last = size - 1;
        } else {
            last = size - 2;
            spline.nodes.add(new SplineNode(nodes.get(0)));
        }
        for (int i = 0; i < last; i++) {
            Node n0 = nodes.get(i);
            Node n1 = nodes.get(i + 1);
            Node n2 = nodes.get(i + 2 < size ? i + 2 : 1);
            EastNorth p0 = n0.getEastNorth();
            EastNorth p1 = n1.getEastNorth();
            EastNorth p2 = n2.getEastNorth();
            double x0 = p0.east();
            double x1 = p1.east();
            double x2 = p2.east();
            double y0 = p0.north();
            double y1 = p1.north();
            double y2 = p2.north();
            double xc1 = (x0 + x1) / 2.0;
            double yc1 = (y0 + y1) / 2.0;
            double xc2 = (x1 + x2) / 2.0;
            double yc2 = (y1 + y2) / 2.0;
            double len1 = sqrt((x1 - x0) * (x1 - x0) + (y1 - y0) * (y1 - y0));
            double len2 = sqrt((x2 - x1) * (x2 - x1) + (y2 - y1) * (y2 - y1));
            double k1 = len1 / (len1 + len2);
            double k2 = len2 / (len1 + len2);
            double dx = xc2 - xc1;
            double dy = yc2 - yc1;
            double xprev = -dx * smooth * k1;
            double yprev = -dy * smooth * k1;
            double xnext = dx * smooth * k2;
            double ynext = dy * smooth * k2;
            SplineNode splineNode = new SplineNode(n1);
            splineNode.cprev = new EastNorth(xprev, yprev);
            splineNode.cnext = new EastNorth(xnext, ynext);
            spline.nodes.add(splineNode);
        }
        if (way.isClosed()) {
            spline.nodes.close();
        } else {
            spline.nodes.add(new SplineNode(nodes.get(size - 1)));
        }
        spline.way = way;
        return spline;
    }
}
