package org.jenkinsci.pipeline_steps_doc_generator;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.jenkinsci.plugins.structs.describable.DescribableModel;
import org.junit.Test;

public class ToAsciidocTest {

    @Test
    public void testDescribeType() {
        DescribableModel<DescribeMe> model = new DescribableModel<>(DescribeMe.class);
        try {
            String desc = ToAsciiDoc.describeType(model.getParameter("s").getType(), "");
            assertEquals(
                    "<li><b>Type:</b> <code>java.util.HashMap&lt;java.lang.String, java.lang.String&gt;</code></li>",
                    desc.trim());
        } catch (Exception e) {
            fail("Cannot describe map " + e);
        }
    }

    @Test
    public void getHelpWithNoStaplerConstructor() throws IOException {
        PipelineStepExtractor pes = new PipelineStepExtractor();
        Path plugins = setupPluginDir(pes);
        downloadCpsPlugin(plugins);
        Map<String, Map<String, List<QuasiDescriptor>>> steps = pes.findSteps();
        QuasiDescriptor parallel = steps.get("workflow-cps").get("Steps").stream()
                .filter(desc -> desc.getSymbol().equals("parallel"))
                .findFirst()
                .orElseThrow();
        String desc = ToAsciiDoc.generateStepHelp(parallel);
        assertThat(desc, containsString("parallel firstBranch"));
        assertThat(desc, not(containsString("Exception")));
    }

    private Path setupPluginDir(PipelineStepExtractor pes) throws IOException {
        Path tempDir = Files.createTempDirectory("plugins");
        pes.homeDir = tempDir.toFile().getAbsolutePath();
        Path plugins = tempDir.resolve("plugins");
        Files.createDirectory(plugins);
        return plugins;
    }

    private void downloadCpsPlugin(Path plugins) throws IOException {
        URL website = new URL(
                "https://updates.jenkins.io/download/plugins/workflow-cps/3697.vb_470e454c232/workflow-cps.hpi");
        ReadableByteChannel rbc = Channels.newChannel(website.openStream());
        try (FileOutputStream fos =
                new FileOutputStream(plugins.resolve("workflow-cps.hpi").toFile())) {
            fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
        }
    }
}
