package org.openstreetmap.josm.plugins.Splinex.importer;

import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.plugins.Splinex.Spline;

import java.util.List;

public interface Importer {
    Spline fromNodes(List<Node> nodes, boolean closed);
}
