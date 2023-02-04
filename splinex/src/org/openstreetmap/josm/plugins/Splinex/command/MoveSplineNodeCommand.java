package org.openstreetmap.josm.plugins.Splinex.command;

import org.openstreetmap.josm.command.MoveCommand;
import org.openstreetmap.josm.data.coor.EastNorth;
import org.openstreetmap.josm.data.osm.Node;

import java.awt.event.MouseEvent;

public class MoveSplineNodeCommand extends MoveCommand implements DragCommand {

    public MoveSplineNodeCommand(Node node) {
        super(node, node.getEastNorth(), node.getEastNorth());
    }

    @Override
    public void dragTo(EastNorth en, MouseEvent e) {
        applyVectorTo(en);
    }
}
