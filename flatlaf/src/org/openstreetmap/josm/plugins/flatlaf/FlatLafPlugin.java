// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.flatlaf;

import javax.swing.UIManager;

import org.openstreetmap.josm.plugins.Plugin;
import org.openstreetmap.josm.plugins.PluginInformation;

import com.formdev.flatlaf.FlatDarculaLaf;
import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatIntelliJLaf;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatLightLaf;

/**
 * FlatLaf for JOSM
 */
public class FlatLafPlugin extends Plugin {

    /**
     * Constructs a new {@code FlatLafPlugin}.
     *
     * @param info plugin info
     */
    public FlatLafPlugin(PluginInformation info) {
        super(info);
        UIManager.getDefaults().put("ClassLoader", getClass().getClassLoader());
        UIManager.installLookAndFeel("FlatLaf Darcula", FlatDarculaLaf.class.getName());
        UIManager.installLookAndFeel("FlatLaf Dark", FlatDarkLaf.class.getName());
        UIManager.installLookAndFeel("FlatLaf IntelliJ", FlatIntelliJLaf.class.getName());
        UIManager.installLookAndFeel("FlatLaf Light", FlatLightLaf.class.getName());

        // enable loading of FlatLaf.properties, FlatLightLaf.properties and FlatDarkLaf.properties from package
        FlatLaf.registerCustomDefaultsSource("org.openstreetmap.josm.plugins.flatlaf", getClass().getClassLoader());
    }

}
