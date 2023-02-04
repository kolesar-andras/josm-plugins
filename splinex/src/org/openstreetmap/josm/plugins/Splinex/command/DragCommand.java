package org.openstreetmap.josm.plugins.Splinex.command;

import org.openstreetmap.josm.data.coor.EastNorth;

import java.awt.event.MouseEvent;

public interface DragCommand {
    void dragTo(EastNorth en, MouseEvent e);
}
