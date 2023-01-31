package org.openstreetmap.josm.plugins.Splinex.command;

import org.openstreetmap.josm.command.Command;
import org.openstreetmap.josm.data.osm.DefaultNameFormatter;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.plugins.Splinex.Spline;
import org.openstreetmap.josm.tools.ImageProvider;

import javax.swing.*;
import java.util.Collection;
import java.util.Collections;

import static org.openstreetmap.josm.tools.I18n.tr;

public class DeleteSplineNodeCommand extends Command {
    private final Spline spline;
    int idx;
    Spline.SNode sn;
    boolean wasDeleted;
    boolean affected;

    public DeleteSplineNodeCommand(Spline spline, int idx) {
        super(MainApplication.getLayerManager().getEditDataSet());
        this.spline = spline;
        this.idx = idx;
    }

    private boolean deleteUnderlying() {
        return !sn.node.hasKeys() && sn.node.getReferrers().isEmpty() && (!spline.isClosed() || idx < (spline.nodes.size() - 1));
    }

    @Override
    public boolean executeCommand() {
        if (spline.isClosed() && idx == 0)
            idx = spline.nodes.size() - 1;
        sn = spline.nodes.get(idx);
        wasDeleted = sn.node.isDeleted();
        if (deleteUnderlying()) {
            sn.node.setDeleted(true);
            affected = true;
        }
        spline.nodes.remove(idx);
        return true;
    }

    @Override
    public void undoCommand() {
        affected = false;
        sn.node.setDeleted(wasDeleted);
        spline.nodes.add(idx, sn);
    }

    @Override
    public String getDescriptionText() {
        return tr("Delete spline node {0}", sn.node.getDisplayName(DefaultNameFormatter.getInstance()));
    }

    @Override
    public void fillModifiedData(Collection<OsmPrimitive> modified, Collection<OsmPrimitive> deleted,
                                 Collection<OsmPrimitive> added) {
        if (deleteUnderlying())
            deleted.add(sn.node);
    }

    @Override
    public Icon getDescriptionIcon() {
        return ImageProvider.get("data", "node");
    }

    @Override
    public Collection<? extends OsmPrimitive> getParticipatingPrimitives() {
        return affected ? Collections.singleton(sn.node) : super.getParticipatingPrimitives();
    }
}
