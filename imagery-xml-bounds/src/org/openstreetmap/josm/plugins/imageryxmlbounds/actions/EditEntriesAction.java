// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.imageryxmlbounds.actions;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JTable;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import org.openstreetmap.josm.actions.JosmAction;
import org.openstreetmap.josm.data.imagery.ImageryInfo;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.preferences.imagery.ImageryProvidersPanel.ImageryDefaultLayerTableModel;
import org.openstreetmap.josm.gui.util.GuiHelper;
import org.openstreetmap.josm.plugins.imageryxmlbounds.XmlBoundsConstants;
import org.openstreetmap.josm.plugins.imageryxmlbounds.XmlBoundsLayer;
import org.openstreetmap.josm.plugins.imageryxmlbounds.data.XmlBoundsConverter;
import org.openstreetmap.josm.tools.ImageProvider;

/**
 * Edit bounds for selected defaults.
 */
public class EditEntriesAction extends JosmAction implements XmlBoundsConstants, ListSelectionListener {

    protected static final String ACTION_NAME = tr("Edit");

    private final JTable defaultTable;
    private final ImageryDefaultLayerTableModel defaultModel;

    private final List<ImageryInfo> entries;

    /**
     * Constructs a new {@code EditEntriesAction}.
     * @param defaultTable parent table
     * @param defaultModel table model
     */
    public EditEntriesAction(JTable defaultTable, ImageryDefaultLayerTableModel defaultModel) {
        putValue(SHORT_DESCRIPTION, tr("edit bounds for selected defaults"));
        putValue(NAME, ACTION_NAME);
        new ImageProvider("imagery_xml_bounds_icon").getResource().attachImageIcon(this, true);
        this.defaultModel = defaultModel;
        this.defaultTable = defaultTable;
        this.defaultTable.getSelectionModel().addListSelectionListener(this);
        this.entries = new ArrayList<>();
        setEnabled(false);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        final XmlBoundsLayer layer = new XmlBoundsLayer(
                XmlBoundsConverter.convertImageryEntries(entries));
        GuiHelper.runInEDT(() -> {
		    MainApplication.getLayerManager().addLayer(layer);
		    layer.onPostLoadFromFile();
		});
    }

    @Override
    public void valueChanged(ListSelectionEvent e) {
        if (!e.getValueIsAdjusting()) {
            entries.clear();
            for (int row : defaultTable.getSelectedRows()) {
                ImageryInfo info = defaultModel.getRow(defaultTable.convertRowIndexToModel(row));
                if (info != null && info.getBounds() != null) {
                    entries.add(info);
                }
            }
            setEnabled(!entries.isEmpty());
        }
    }
}
