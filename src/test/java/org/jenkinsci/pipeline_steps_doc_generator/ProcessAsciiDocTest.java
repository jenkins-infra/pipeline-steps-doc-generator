package org.jenkinsci.pipeline_steps_doc_generator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import org.apache.commons.io.FileUtils;
import org.junit.Test;

public class ProcessAsciiDocTest {
    String allAscii = "src/test/resources/input/";
    File child = new File(allAscii + "/workflow-scm.adoc");

    @Test
    public void isExceptionThrown() {
        ProcessAsciiDoc pad = new ProcessAsciiDoc();
        assertThrows(RuntimeException.class, () -> {
            pad.separateClass("$class: 'PvcsScm'", allAscii, child, 500);
        });
    }

    @Test
    public void isNewAdocCreated() {
        ProcessAsciiDoc pad = new ProcessAsciiDoc();
        try {
            pad.separateClass("$class: 'GitSCM'", allAscii, child, 500);
            File file = new File("src/test/resources/input/params/gitscm.adoc");
            assertTrue(file.exists());
            file.delete();
        } catch (IOException ex) {
            ex.printStackTrace();
            assertTrue("IOException occurred", false);
        }
    }

    @Test
    public void isSeparatedContentCorrect() {
        ProcessAsciiDoc pad = new ProcessAsciiDoc();
        try {
            pad.separateClass("$class: 'GitSCM'", allAscii, child, 500);
            File file1 = new File("src/test/resources/input/params/gitscm.adoc");
            File file2 = new File("src/test/resources/input/compare/gitscm.adoc");
            assertTrue(FileUtils.contentEquals(file1, file2));
            file1.delete();
        } catch (IOException ex) {
            assertTrue("IOException occurred", false);
        }
    }

    @Test
    public void isHyperlinkReplacedCorrectly() {
        ProcessAsciiDoc pad = new ProcessAsciiDoc();
        try {
            String[] lines =
                    pad.separateClass("$class: 'GitSCM'", allAscii, child, 500).split("\n");
            String link = "";
            File file = new File("src/test/resources/input/params/gitscm.adoc");
            file.delete();
            for (String line : lines) {
                if (line.contains("class: 'GitSCM'")) {
                    link = line;
                    break;
                }
            }
            assertEquals(
                    "<li><span><a href=\"/doc/pipeline/steps/params/gitscm\"><code>$class: 'GitSCM'</code></a></span></li>",
                    link);
        } catch (IOException ex) {
            assertTrue("IOException occurred", false);
        }
    }
}
