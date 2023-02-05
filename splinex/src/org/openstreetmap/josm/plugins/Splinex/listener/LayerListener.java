package org.openstreetmap.josm.plugins.Splinex.listener;

import org.openstreetmap.josm.gui.layer.Layer;
import org.openstreetmap.josm.gui.layer.LayerManager.LayerAddEvent;
import org.openstreetmap.josm.gui.layer.LayerManager.LayerOrderChangeEvent;
import org.openstreetmap.josm.gui.layer.LayerManager.LayerRemoveEvent;
import org.openstreetmap.josm.gui.layer.MainLayerManager.ActiveLayerChangeEvent;
import org.openstreetmap.josm.gui.layer.MainLayerManager.ActiveLayerChangeListener;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;
import org.openstreetmap.josm.gui.layer.LayerManager.LayerChangeListener;
import org.openstreetmap.josm.plugins.Splinex.Spline;

import java.util.HashMap;
import java.util.Map;

import static org.openstreetmap.josm.gui.MainApplication.getLayerManager;

public class LayerListener implements LayerChangeListener, ActiveLayerChangeListener {

    protected Spline splCached;
    protected Map<Layer, Spline> layerSplines = new HashMap<>();

    public void register() {
        getLayerManager().addLayerChangeListener(this);
        getLayerManager().addActiveLayerChangeListener(this);
    }

    public Spline getSpline() {
        if (splCached != null)
            return splCached;
        Layer l = getLayerManager().getEditLayer();
        if (!(l instanceof OsmDataLayer))
            return null;
        splCached = layerSplines.get(l);
        if (splCached == null)
            splCached = new Spline();
        layerSplines.put(l, splCached);
        return splCached;
    }

    public boolean hasActiveSpline() {
        Spline spline = getSpline();
        return spline != null && !spline.isEmpty();
    }

    @Override
    public void activeOrEditLayerChanged(ActiveLayerChangeEvent e) {
        splCached = layerSplines.get(getLayerManager().getActiveLayer());
    }

    @Override
    public void layerOrderChanged(LayerOrderChangeEvent e) {
    }

    @Override
    public void layerAdded(LayerAddEvent e) {
    }

    @Override
    public void layerRemoving(LayerRemoveEvent e) {
        layerSplines.remove(e.getRemovedLayer());
        splCached = null;
    }

}
