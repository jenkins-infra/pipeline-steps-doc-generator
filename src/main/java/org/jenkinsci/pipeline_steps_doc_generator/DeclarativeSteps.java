package org.jenkinsci.pipeline_steps_doc_generator;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.io.FileUtils;
import org.jenkinsci.infra.tools.HyperLocalPluginManager;
import org.jenkinsci.plugins.pipeline.modeldefinition.agent.DeclarativeAgentDescriptor;
import org.jenkinsci.plugins.pipeline.modeldefinition.options.DeclarativeOptionDescriptor;
import org.jenkinsci.plugins.pipeline.modeldefinition.when.DeclarativeStageConditionalDescriptor;
import org.jenkinsci.plugins.structs.SymbolLookup;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Computer;
import hudson.model.Descriptor;
import hudson.model.JobPropertyDescriptor;
import hudson.model.ParameterDefinition;
import hudson.triggers.TriggerDescriptor;

public class DeclarativeSteps {
    private static final Logger LOG = Logger.getLogger(DeclarativeSteps.class.getName());
    private static final List<String> blockedOptionSteps = Arrays.asList("node", "stage", "withEnv", "script", "withCredentials");
    private static final List<Class<?>> blockedOptionStepContexts = Arrays.asList(Launcher.class, FilePath.class, Computer.class);

    public void generateDeclarativeAscii(String declarativeDest, HyperLocalPluginManager pluginManager) {
        File declDest;
        if (declarativeDest != null) {
            declDest = new File(declarativeDest);
        } else {
            declDest = new File("declarative");
        }
        declDest.mkdirs();
        String declPath = declDest.getAbsolutePath();

        Map<Class<? extends Descriptor>, Predicate<Descriptor>> filters = getDeclarativeFilters();

        for (Map.Entry<String,List<Class<? extends Descriptor>>> entry : getDeclarativeDirectives().entrySet()) {
            LOG.info("Generating docs for directive " + entry.getKey());
            Map<String, List<Descriptor>> pluginDescMap = new HashMap<>();

            for (Class<? extends Descriptor> d : entry.getValue()) {
                Predicate<Descriptor> filter = filters.get(d);
                LOG.info(" - Loading descriptors of type " + d.getSimpleName() + " with filter: " + (filter != null));
                pluginDescMap = processDescriptors(d, pluginDescMap, filter, pluginManager);
            }

            String whole9yards = ToAsciiDoc.generateDirectiveHelp(entry.getKey(), pluginDescMap, true);

            try{
                FileUtils.writeStringToFile(new File(declPath, entry.getKey() + ".adoc"), whole9yards, StandardCharsets.UTF_8);
            } catch (Exception ex) {
                LOG.log(Level.SEVERE, "Error generating directive file for " + entry.getKey() + ".  Skip.", ex);
                //continue to next directive
            }
        }
    }

    private Map<String, List<Descriptor>> processDescriptors(@NonNull Class<? extends Descriptor> c,
                                                             @NonNull Map<String, List<Descriptor>> descMap,
                                                             @CheckForNull Predicate<Descriptor> filter, 
                                                             HyperLocalPluginManager pluginManager) {
        // If no filter was specified, default to true.
        if (filter == null) {
            filter = (d) -> true;
        }
        List<? extends Descriptor> descriptors = pluginManager.getPluginStrategy().findComponents(c);
        final Map<String, String> descriptorsToPlugin = pluginManager.uberPlusClassLoader.getByPlugin();

        // Only include steps or describables with symbols.
        Predicate<Descriptor> fullFilter = filter.and(d -> {
            if (d instanceof StepDescriptor) {
                return true;
            } else {
                Set<String> symbols = SymbolLookup.getSymbolValue(d);
                return !symbols.isEmpty();
            }
        });

        descriptors.stream().filter(fullFilter).forEach(d -> {
            String pluginName = descriptorsToPlugin.get(d.getClass().getName());
            if (pluginName != null) {
                pluginName = pluginName.trim();
            } else {
                pluginName = "core";
            }
            descMap.computeIfAbsent(pluginName, k -> new ArrayList<>()).add(d);
        });
        return descMap;
    }

    private Map<String,List<Class<? extends Descriptor>>> getDeclarativeDirectives() {
        Map<String,List<Class<? extends Descriptor>>> directives = new HashMap<>();

        directives.put("agent", Arrays.asList(DeclarativeAgentDescriptor.class));
        directives.put("options", Arrays.asList(JobPropertyDescriptor.class, DeclarativeOptionDescriptor.class, StepDescriptor.class));
        directives.put("triggers", Arrays.asList(TriggerDescriptor.class));
        directives.put("parameters", Arrays.asList(ParameterDefinition.ParameterDescriptor.class));
        directives.put("when", Arrays.asList(DeclarativeStageConditionalDescriptor.class));

        return directives;
    }
    
    private Map<Class<? extends Descriptor>, Predicate<Descriptor>> getDeclarativeFilters() {
        Map<Class<? extends Descriptor>, Predicate<Descriptor>> filters = new HashMap<>();

        filters.put(StepDescriptor.class, d -> {
            if (d instanceof StepDescriptor) {
                StepDescriptor s = (StepDescriptor) d;
                // Note that this is lifted from org.jenkinsci.plugins.pipeline.modeldefinition.model.Options, with the
                // blocked steps hardcoded. This could be better.
                return s.takesImplicitBlockArgument() &&
                        s.getRequiredContext().stream().noneMatch(blockedOptionStepContexts::contains) &&
                        !blockedOptionSteps.contains(s.getFunctionName());
            } else {
                return false;
            }
        });

        return filters;
    }
}