package org.jenkinsci.pipeline_steps_doc_generator;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.io.FileMatchers.aReadableFile;
import static org.junit.Assert.assertThrows;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import org.junit.After;
import org.junit.Test;

public class ProcessAsciiDocTest {
    private String allAscii = "src/test/resources/input/";
    private File child = new File(allAscii + "workflow-scm.adoc");

    private String GIT_SCM_PARAMS_NAME = allAscii + "params/gitscm.adoc";
    private String GIT_SCM_COMPARE_NAME = GIT_SCM_PARAMS_NAME.replace("params", "compare");

    private ProcessAsciiDoc pad = new ProcessAsciiDoc();

    @After
    public void deleteGeneratedFiles() {
        File file = new File(GIT_SCM_PARAMS_NAME);
        file.delete();
    }

    @Test
    public void isExceptionThrown() {
        assertThrows(RuntimeException.class, () -> {
            pad.separateClass("$class: 'PvcsScm'", allAscii, child, 500);
        });
    }

    @Test
    public void isNewAdocCreated() throws Exception {
        pad.separateClass("$class: 'GitSCM'", allAscii, child, 500);
        assertThat(new File(GIT_SCM_PARAMS_NAME), aReadableFile());
    }

    private String readFile(String fileName) throws Exception {
        return new String(Files.readAllBytes(Paths.get(fileName)), StandardCharsets.UTF_8);
    }

    @Test
    public void isSeparatedContentCorrect() throws Exception {
        pad.separateClass("$class: 'GitSCM'", allAscii, child, 500);
        String generatedContent = readFile(GIT_SCM_PARAMS_NAME);
        String referenceContent = readFile(GIT_SCM_COMPARE_NAME);
        assertThat(generatedContent, is(referenceContent));
    }

    @Test
    public void isHyperlinkReplacedCorrectly() throws Exception {
        List<String> content = Arrays.asList(
                pad.separateClass("$class: 'GitSCM'", allAscii, child, 500).split("\n"));
        String expected =
                "<li><span><a href=\"/doc/pipeline/steps/params/gitscm\"><code>$class: 'GitSCM'</code></a></span></li>";
        assertThat(content, hasItem(expected));
    }
}
