package org.jenkinsci.pipeline_steps_doc_generator;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;

import org.junit.Test;

public class ProcessAsciiDocTest {
    String allAscii = "src/test/resources/input/";
    File dir = new File(allAscii);
    File[] directoryListing = dir.listFiles();

    @Test
    public void isExceptionThrown() {
        ProcessAsciiDoc pad = new ProcessAsciiDoc();
        File child = directoryListing[0];
        try {
            pad.separateClass("$class: 'PvcsScm'", allAscii, child, 500);
            assertTrue(false);
        } catch (RuntimeException ex) {
            assertTrue(true);
        } catch (IOException ex) {
            assertTrue(false);
        }
    }

    @Test
    public void isNewAdocCreated() {
        ProcessAsciiDoc pad = new ProcessAsciiDoc();
        File child = directoryListing[0];
        try {
            pad.separateClass("$class: 'GitSCM'", allAscii, child, 500);
            File file = new File("src/test/resources/input/params/gitscm.adoc");
            assertTrue(file.exists());
            file.delete();
        } catch (RuntimeException ex) {
            assertTrue(false);
        } catch (IOException ex) {
            assertTrue(false);
        }
    }
}
