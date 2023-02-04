package org.openstreetmap.josm.plugins.Splinex;

import org.openstreetmap.josm.data.osm.Node;

public class NodeHighlight {

    public Node node;

    public boolean set(Node n) {
        if (node == n) {
            return false;
        }
        unset();
        node = n;
        n.setHighlighted(true);
        return true;
    }

    public boolean unset() {
        if (node != null) {
            node.setHighlighted(false);
            node = null;
            return true;
        }
        return false;
    }

    public boolean isNodeDeleted() {
        if (node == null) {
            return false;
        } else {
            return node.isDeleted();
        }
    }

}
