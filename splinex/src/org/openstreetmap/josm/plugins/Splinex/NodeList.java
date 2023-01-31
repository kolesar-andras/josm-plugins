package org.openstreetmap.josm.plugins.Splinex;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * We keep a list of all nodes but hide deleted nodes;
 * other tools may delete one or more nodes;
 * deleting a node permanently is not an option
 * because undo can restore them;
 * this list hides deleted items
 */

class NodeList extends ArrayList<Spline.SNode> {
    protected final int NONE = -1;

    class NodeIterator implements Iterator {
        protected int cursor;

        protected int getRealIndex() {
            return cursor-1;
        }

        @Override
        public boolean hasNext() {
            return nextValidIndex() != NONE;
        }

        @Override
        public Spline.SNode next() {
            int index = nextValidIndex();
            if (index == NONE) throw new NoSuchElementException();
            cursor = index+1;
            return getItemAtIndex(index);
        }

        protected int nextValidIndex() {
            for (int i=cursor; i<NodeList.super.size(); i++) {
                if (!getItemAtIndex(i).node.isDeleted()) return i;
            }
            return NONE;
        }

        protected Spline.SNode getItemAtIndex(int index) {
            return NodeList.super.get(index);
        }
    }

    class NodeReverseIterator extends NodeIterator {
        @Override
        protected Spline.SNode getItemAtIndex(int index) {
            return NodeList.super.get(NodeList.super.size()-1-index);
        }
    }

    @Override
    public Iterator iterator() {
        return new NodeIterator();
    }

    @Override
    public boolean isEmpty() {
        return size() == 0;
    }

    @Override
    public int size() {
        int size = 0;
        for (NodeIterator it = new NodeIterator(); it.hasNext(); it.next()) {
            size++;
        }
        return size;
    }

    @Override
    public Spline.SNode get(int index) {
        return super.get(getRealIndex(index));
    }

    @Override
    public void add(int index, Spline.SNode item) {
        if (index == size()) {
            super.add(item);
        } else {
            super.add(getRealIndex(index), item);
        }
    }

    @Override
    public Spline.SNode remove(int index) {
        return super.remove(getRealIndex(index));
    }

    public Spline.SNode getFirst() {
        return new NodeIterator().next();
    }

    public Spline.SNode getLast() {
        return new NodeReverseIterator().next();
    }

    protected int getRealIndex(int index) {
        NodeList.NodeIterator iterator = new NodeList.NodeIterator();
        for (int i = 0; i <= index; i++) {
            iterator.next();
        }
        return iterator.getRealIndex();
    }
}
