package org.jenkinsci.pipeline_steps_doc_generator;

import org.kohsuke.stapler.DataBoundConstructor;

import java.util.HashMap;

class DescribeMe {
    @DataBoundConstructor
    public DescribeMe(HashMap<String, String> s) {
        this.s = s;
    }

    public HashMap<String, String> s;
}
