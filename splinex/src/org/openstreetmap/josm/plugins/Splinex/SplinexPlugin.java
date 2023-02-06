// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.Splinex;

import org.openstreetmap.josm.gui.IconToggleButton;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.MapFrame;
import org.openstreetmap.josm.gui.preferences.PreferenceSetting;
import org.openstreetmap.josm.plugins.Plugin;
import org.openstreetmap.josm.plugins.PluginInformation;

/**
 * <strong>SplinexPlugin</strong> is the main class for the <tt>Splinex</tt>
 * plugin.
 */
public class SplinexPlugin extends Plugin {
    public static final double EPSILON = 0.0000000000001;

    public SplinexPlugin(PluginInformation info) {
        super(info);
    }

    /**
     * Called when the JOSM map frame is created or destroyed.
     */
    @Override
    public void mapFrameInitialized(MapFrame oldFrame, MapFrame newFrame) {
        if (oldFrame == null && newFrame != null) { // map frame added
            MapFrame map = MainApplication.getMap();
            DrawSplineAction drawSplineAction = new DrawSplineAction(map);
            SplinePainter splinePainter = new SplinePainter(drawSplineAction);
            map.addMapMode(new IconToggleButton(drawSplineAction));
            map.mapView.addTemporaryLayer(splinePainter);
        }
    }

    @Override
    public PreferenceSetting getPreferenceSetting() {
        return new SplinexPreference();
    }
}
