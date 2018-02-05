package org.jenkinsci.pipeline_steps_doc_generator;

import hudson.model.Descriptor;
import java.util.Set;
import org.jenkinsci.plugins.structs.SymbolLookup;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;

/**
 * Generalization of {@link StepDescriptor} to handle metasteps.
 * Akin to the nested type in {@code Snippetizer}.
 */
public class QuasiDescriptor {

    public final Descriptor<?> real;

    public QuasiDescriptor(Descriptor<?> d) {
        real = d;
    }

    public String getSymbol() {
        if (real instanceof StepDescriptor) {
            return ((StepDescriptor) real).getFunctionName();
        } else {
            Set<String> symbolValues = SymbolLookup.getSymbolValue(real);
            if (!symbolValues.isEmpty()) {
                return symbolValues.iterator().next();
            } else {
                throw new AssertionError("Symbol present but no values defined.");
            }
        }
    }

}
