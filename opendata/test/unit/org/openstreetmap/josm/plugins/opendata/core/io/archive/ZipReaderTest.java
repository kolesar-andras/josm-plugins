// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.opendata.core.io.archive;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Map.Entry;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.plugins.opendata.core.io.NonRegFunctionalTests;
import org.openstreetmap.josm.testutils.JOSMTestRules;
import org.openstreetmap.josm.testutils.annotations.BasicPreferences;
import org.openstreetmap.josm.tools.Logging;

/**
 * Unit tests of {@link ZipReader} class.
 */
@BasicPreferences
class ZipReaderTest {

    /**
     * Setup test.
     */
    @RegisterExtension
    public JOSMTestRules rules = new JOSMTestRules().projection().noTimeout();

    /**
     * Test for various zip files reading
     * @throws Exception if an error occurs during reading
     */
    @Test
    void testReadZipFiles() throws Exception {
        for (Path p : NonRegFunctionalTests.listDataFiles("zip")) {
            File zipfile = p.toFile();
            Logging.info("Testing reading file "+zipfile.getPath());
            try (InputStream is = new FileInputStream(zipfile)) {
                for (Entry<File, DataSet> entry : ZipReader.parseDataSets(is, null, null, false).entrySet()) {
                    String name = entry.getKey().getName();
                    Logging.info("Checking dataset for entry "+name);
                    NonRegFunctionalTests.testGeneric(zipfile.getName()+"/"+name, entry.getValue());
                }
            }
        }
    }
}
