package org.openstreetmap.josm.plugins.Splinex;

import org.openstreetmap.josm.data.osm.OsmPrimitive;

public class Highlight<T extends OsmPrimitive> {

    private T osmPrimitive;

    public boolean set(T n) {
        if (osmPrimitive == n) {
            return false;
        }
        unset();
        osmPrimitive = n;
        n.setHighlighted(true);
        return true;
    }

    public boolean unset() {
        if (osmPrimitive != null) {
            osmPrimitive.setHighlighted(false);
            osmPrimitive = null;
            return true;
        }
        return false;
    }

    public boolean isDeleted() {
        if (osmPrimitive == null) {
            return false;
        } else {
            return osmPrimitive.isDeleted();
        }
    }

    public boolean isActive() {
        return osmPrimitive != null;
    }

    public T get() {
        return osmPrimitive;
    }
}
