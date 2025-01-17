// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.buildings_tools;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.util.Map;

import javax.swing.JCheckBox;
import javax.swing.JLabel;

import org.openstreetmap.josm.data.osm.Changeset;
import org.openstreetmap.josm.gui.tagging.TagEditorModel;
import org.openstreetmap.josm.gui.tagging.TagEditorPanel;
import org.openstreetmap.josm.tools.GBC;

public class AdvancedSettingsDialog extends MyDialog {
    private final TagEditorModel tagsModel = new TagEditorModel();

    private final JCheckBox cBigMode = new JCheckBox(tr("Big buildings mode"));
    private final JCheckBox cSoftCur = new JCheckBox(tr("Rotate crosshair"));
    private final JCheckBox cNoClickDrag = new JCheckBox(tr("Disable click+drag"));

    public AdvancedSettingsDialog() {
        super(tr("Advanced settings"));

        panel.add(new JLabel(tr("Buildings tags:")), GBC.eol().fill(GBC.HORIZONTAL));

        for (Map.Entry<String, String> entry : ToolSettings.getTags().entrySet()) {
            tagsModel.add(entry.getKey(), entry.getValue());
        }
        panel.add(new TagEditorPanel(tagsModel, null, Changeset.MAX_CHANGESET_TAG_LENGTH), GBC.eop().fill(GBC.BOTH));

        panel.add(cBigMode, GBC.eol().fill(GBC.HORIZONTAL));
        panel.add(cSoftCur, GBC.eol().fill(GBC.HORIZONTAL));
        panel.add(cNoClickDrag, GBC.eol().fill(GBC.HORIZONTAL));

        cBigMode.setSelected(ToolSettings.isBBMode());
        cSoftCur.setSelected(ToolSettings.isSoftCursor());
        cNoClickDrag.setSelected(ToolSettings.isNoClickAndDrag());

        setupDialog();
        showDialog();
    }

    public final void saveSettings() {
        ToolSettings.saveTags(tagsModel.getTags());
        ToolSettings.setBBMode(cBigMode.isSelected());
        ToolSettings.setSoftCursor(cSoftCur.isSelected());
        ToolSettings.setNoClickAndDrag(cNoClickDrag.isSelected());
    }
}
