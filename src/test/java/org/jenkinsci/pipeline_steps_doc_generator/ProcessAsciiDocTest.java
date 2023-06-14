package org.jenkinsci.pipeline_steps_doc_generator;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.io.FileMatchers.aReadableFile;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import org.apache.commons.io.FileUtils;
import org.junit.Test;

public class ProcessAsciiDocTest {
    private String allAscii = "src/test/resources/input/";
    private File allAsciiDir = new File(allAscii);
    private File child = new File(allAsciiDir, "workflow-scm.adoc");

    private String GIT_SCM_PARAMS_NAME = allAscii + "params/gitscm.adoc";

    @Test
    public void isExceptionThrown() {
        ProcessAsciiDoc pad = new ProcessAsciiDoc();
        assertThrows(RuntimeException.class, () -> {
            pad.separateClass("$class: 'PvcsScm'", allAscii, child, 500);
        });
    }

    @Test
    public void isNewAdocCreated() throws Exception {
        ProcessAsciiDoc pad = new ProcessAsciiDoc();
        pad.separateClass("$class: 'GitSCM'", allAscii, child, 500);
        File file = new File(GIT_SCM_PARAMS_NAME);
        assertThat(file, aReadableFile());
        file.delete();
    }

    @Test
    public void isSeparatedContentCorrect() throws Exception {
        ProcessAsciiDoc pad = new ProcessAsciiDoc();
        pad.separateClass("$class: 'GitSCM'", allAscii, child, 500);
        File file1 = new File(GIT_SCM_PARAMS_NAME);
        File file2 = new File("src/test/resources/input/compare/gitscm.adoc");
        assertTrue(FileUtils.contentEquals(file1, file2));
        file1.delete();
    }

    @Test
    public void isHyperlinkReplacedCorrectly() {
        ProcessAsciiDoc pad = new ProcessAsciiDoc();
        try {
            String[] lines =
                    pad.separateClass("$class: 'GitSCM'", allAscii, child, 500).split("\n");
            String link = "";
            File file = new File(GIT_SCM_PARAMS_NAME);
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
