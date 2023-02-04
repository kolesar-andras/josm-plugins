package org.openstreetmap.josm.plugins.Splinex.command;

import org.openstreetmap.josm.command.Command;
import org.openstreetmap.josm.data.osm.DefaultNameFormatter;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.plugins.Splinex.Spline;
import org.openstreetmap.josm.plugins.Splinex.SplineNode;
import org.openstreetmap.josm.tools.ImageProvider;

import javax.swing.*;
import java.util.Collection;
import java.util.Collections;

import static org.openstreetmap.josm.tools.I18n.tr;

public class AddSplineNodeCommand extends Command {
    private final Spline spline;
    private final SplineNode sn;
    private final boolean existing;
    private final int idx;
    boolean affected;

    public AddSplineNodeCommand(Spline spline, SplineNode sn, boolean existing, int idx) {
        super(MainApplication.getLayerManager().getEditDataSet());
        this.spline = spline;
        this.sn = sn;
        this.existing = existing;
        this.idx = idx;
    }

    public AddSplineNodeCommand(Spline spline, SplineNode sn, boolean existing) {
        this(spline, sn, existing, spline.nodes.size() - 1);
    }

    @Override
    public boolean executeCommand() {
        spline.nodes.add(idx, sn);
        if (!existing) {
            getAffectedDataSet().addPrimitive(sn.node);
            sn.node.setModified(true);
            affected = true;
        }
        return true;
    }

    @Override
    public void undoCommand() {
        if (!existing)
            getAffectedDataSet().removePrimitive(sn.node);
        spline.nodes.remove(idx);
        affected = false;
    }

    @Override
    public String getDescriptionText() {
        if (existing)
            return tr("Add an existing node to spline: {0}",
                sn.node.getDisplayName(DefaultNameFormatter.getInstance()));
        return tr("Add a new node to spline: {0}", sn.node.getDisplayName(DefaultNameFormatter.getInstance()));
    }

    @Override
    public void fillModifiedData(Collection<OsmPrimitive> modified, Collection<OsmPrimitive> deleted,
                                 Collection<OsmPrimitive> added) {
        if (!existing)
            added.add(sn.node);
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
