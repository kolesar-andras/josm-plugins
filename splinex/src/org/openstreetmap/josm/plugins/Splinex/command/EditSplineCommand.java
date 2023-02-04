package org.openstreetmap.josm.plugins.Splinex.command;

import org.openstreetmap.josm.command.Command;
import org.openstreetmap.josm.data.coor.EastNorth;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.plugins.Splinex.SplineNode;
import org.openstreetmap.josm.tools.ImageProvider;

import javax.swing.*;
import java.util.Collection;

public class EditSplineCommand extends Command {
    EastNorth cprev;
    EastNorth cnext;
    SplineNode sn;

    public EditSplineCommand(SplineNode sn) {
        super(MainApplication.getLayerManager().getEditDataSet());
        this.sn = sn;
        cprev = sn.cprev.add(0, 0);
        cnext = sn.cnext.add(0, 0);
    }

    @Override
    public boolean executeCommand() {
        EastNorth en = sn.cprev;
        sn.cprev = this.cprev;
        this.cprev = en;
        en = sn.cnext;
        sn.cnext = this.cnext;
        this.cnext = en;
        return true;
    }

    @Override
    public void undoCommand() {
        executeCommand();
    }

    @Override
    public void fillModifiedData(Collection<OsmPrimitive> modified, Collection<OsmPrimitive> deleted,
                                 Collection<OsmPrimitive> added) {
        // This command doesn't touches OSM data
    }

    @Override
    public String getDescriptionText() {
        return "Edit spline";
    }

    @Override
    public Icon getDescriptionIcon() {
        return ImageProvider.get("data", "node");
    }

    public boolean sNodeIs(SplineNode node) {
        return sn == node;
    }
}
