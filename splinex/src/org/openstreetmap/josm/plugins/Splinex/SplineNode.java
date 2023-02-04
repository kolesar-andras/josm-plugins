package org.openstreetmap.josm.plugins.Splinex;

import org.openstreetmap.josm.data.coor.EastNorth;
import org.openstreetmap.josm.data.osm.Node;

public class SplineNode {
    public final Node node; // Endpoint
    public EastNorth cprev, cnext; // Relative offsets of control points

    public SplineNode(Node node) {
        this.node = node;
        cprev = EastNorth.ZERO;
        cnext = EastNorth.ZERO;
    }

    public SplineNode(Node node, EastNorth cprev, EastNorth cnext) {
        this.node = node;
        this.cprev = cprev;
        this.cnext = cnext;
    }
}
