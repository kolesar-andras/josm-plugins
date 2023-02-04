package org.openstreetmap.josm.plugins.Splinex.listener;

import org.openstreetmap.josm.data.osm.event.*;

import static org.openstreetmap.josm.gui.MainApplication.getLayerManager;

public class DatasetListener implements DataSetListener {

    public void register() {
        getLayerManager().getEditLayer().getDataSet().addDataSetListener(this);
    }

    @Override
    public void primitivesAdded(PrimitivesAddedEvent event) {
        getLayerManager().invalidateEditLayer();
    }

    @Override
    public void primitivesRemoved(PrimitivesRemovedEvent event) {
        getLayerManager().invalidateEditLayer();
    }

    @Override
    public void tagsChanged(TagsChangedEvent event) {
    }

    @Override
    public void nodeMoved(NodeMovedEvent event) {
    }

    @Override
    public void wayNodesChanged(WayNodesChangedEvent event) {
    }

    @Override
    public void relationMembersChanged(RelationMembersChangedEvent event) {
    }

    @Override
    public void otherDatasetChange(AbstractDatasetChangedEvent event) {
    }

    @Override
    public void dataChanged(DataChangedEvent event) {
    }

}
