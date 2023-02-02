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

public class NodeList extends ArrayList<Spline.SNode> {
    protected final static int NONE = -1;

    protected boolean closed;

    class NodeIterator implements Iterator<Spline.SNode> {
        protected int cursor;
        protected boolean turned;
        protected int realIndex;

        @Override
        public boolean hasNext() {
            if (nextValidIndex() == NONE) {
                if (closed && !turned) {
                    return nextValidIndex(0) != NONE;
                } else {
                    return false;
                }
            }
            return true;
        }

        @Override
        public Spline.SNode next() {
            int index = nextValidIndex();
            if (index == NONE) {
                if (closed && !turned && NONE != (index = nextValidIndex(0))) {
                    turned = true;
                    return getItemAtIndex(index);
                } else {
                    throw new NoSuchElementException();
                }
            }
            cursor = index+1;
            return getItemAtIndex(index);
        }

        protected int nextValidIndex() {
            return nextValidIndex(cursor);
        }

        protected int nextValidIndex(int index) {
            for (int i=index; i<NodeList.super.size(); i++) {
                if (!getItemAtIndex(i).node.isDeleted()) return i;
            }
            return NONE;
        }

        protected Spline.SNode getItemAtIndex(int index) {
            realIndex = index;
            return NodeList.super.get(index);
        }

        protected int getRealIndex() {
            return realIndex;
        }
    }

    class NodeReverseIterator extends NodeIterator {
        @Override
        protected Spline.SNode getItemAtIndex(int index) {
            return super.getItemAtIndex(NodeList.super.size()-1-index);
        }
    }

    @Override
    public Iterator<Spline.SNode> iterator() {
        return new NodeIterator();
    }

    @Override
    public boolean isEmpty() {
        if (closed) {
            return size() < 3;
        } else {
            return size() == 0;
        }
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
        if (!super.isEmpty() && isEmpty()) {
            // cleanup before creating new spline
            // if previous one had only deleted nodes
            super.clear();
            closed = false;
        }
        if (index == size() - (closed ? 1 : 0)) {
            super.add(item);
        } else {
            super.add(getRealIndex(index), item);
        }
    }

    @Override
    public Spline.SNode remove(int index) {
        return super.remove(getRealIndex(index));
    }

    @Override
    public void clear() {
        super.clear();
        closed = false;
    }

    public Spline.SNode getFirst() {
        return new NodeIterator().next();
    }

    public Spline.SNode getLast() {
        return new NodeReverseIterator().next();
    }

    public void open() {
        closed = false;
    }

    public void close() {
        closed = true;
    }

    public boolean isClosed() {
        return closed && size() >= 3;
    }

    protected int getRealIndex(int index) {
        NodeList.NodeIterator iterator = new NodeList.NodeIterator();
        for (int i = 0; i <= index; i++) {
            iterator.next();
        }
        return iterator.getRealIndex();
    }
}
