package org.jenkinsci.pipeline_steps_doc_generator;

import java.util.HashMap;
import org.kohsuke.stapler.DataBoundConstructor;

class DescribeMe {
    @DataBoundConstructor
    public DescribeMe(HashMap<String, String> s) {
        this.s = s;
    }

    public HashMap<String, String> s;
}
