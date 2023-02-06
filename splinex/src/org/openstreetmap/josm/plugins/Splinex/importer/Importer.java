package org.openstreetmap.josm.plugins.Splinex.importer;

import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.plugins.Splinex.Spline;

public interface Importer {
    Spline fromNodes(Way way);
}
