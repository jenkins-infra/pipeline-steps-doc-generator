package org.jenkinsci.pipeline_steps_doc_generator;
import org.jenkinsci.plugins.structs.describable.DescribableModel;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class ToAsciidocTest {

    @Test
    public void testDescribeType() {
        DescribableModel<DescribeMe> model = new DescribableModel<>(DescribeMe.class);
        try {
            String desc = ToAsciiDoc.describeType(model.getParameter("s").getType(), "");
            assertEquals("<li><b>Type:</b> <code>java.util.HashMap&lt;java.lang.String, java.lang.String&gt;</code></li>", desc.trim());
        } catch (Exception e) {
            fail("Cannot describe map " + e);
        }
    }
}
