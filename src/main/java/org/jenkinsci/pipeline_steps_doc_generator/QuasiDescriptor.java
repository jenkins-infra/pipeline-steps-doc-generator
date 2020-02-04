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
    private StepDescriptor parent;

    public QuasiDescriptor(Descriptor<?> d, StepDescriptor parent) {
        real = d;
        this.parent = parent;
    }

    public String getSymbol() {
        if (real instanceof StepDescriptor) {
            return ((StepDescriptor) real).getFunctionName();
        } else {
            Set<String> symbolValues = SymbolLookup.getSymbolValue(real);
            if (!symbolValues.isEmpty()) {
                return symbolValues.iterator().next();
            } else if(parent != null) {
                return String.format("%s([$class: '%s'])", parent.getFunctionName(), real.clazz.getSimpleName());
            } else {
                throw new AssertionError("Symbol present but no values defined.");
            }
        }
    }

}
