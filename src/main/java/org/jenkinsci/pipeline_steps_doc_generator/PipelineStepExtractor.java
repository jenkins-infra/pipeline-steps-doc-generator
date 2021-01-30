package org.jenkinsci.pipeline_steps_doc_generator;

import hudson.FilePath;
import hudson.Launcher;
import hudson.MockJenkins;
import hudson.PluginManager;
import hudson.PluginWrapper;
import hudson.init.InitMilestone;
import hudson.init.InitStrategy;
import hudson.model.Computer;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.JobPropertyDescriptor;
import hudson.model.ParameterDefinition;
import hudson.security.ACL;
import hudson.security.ACLContext;
import hudson.triggers.TriggerDescriptor;
import jenkins.InitReactorRunner;
import jenkins.model.Jenkins;
import org.apache.commons.io.FileUtils;
import org.jenkinsci.plugins.pipeline.modeldefinition.agent.DeclarativeAgentDescriptor;
import org.jenkinsci.plugins.pipeline.modeldefinition.options.DeclarativeOptionDescriptor;
import org.jenkinsci.plugins.pipeline.modeldefinition.when.DeclarativeStageConditionalDescriptor;
import org.jenkinsci.plugins.structs.SymbolLookup;
import org.jenkinsci.plugins.structs.describable.DescribableModel;
import org.jenkinsci.plugins.structs.describable.DescribableParameter;
import org.jenkinsci.plugins.structs.describable.HeterogeneousObjectType;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jvnet.hudson.reactor.*;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

import static org.mockito.Mockito.*;

/**
 * Process and find all the Pipeline steps definied in Jenkins plugins.
 */
public class PipelineStepExtractor {
    private static final Logger LOG = Logger.getLogger(PipelineStepExtractor.class.getName());
    static {
        System.setProperty("java.util.logging.SimpleFormatter.format","[%4$-7s] %5$s %6$s%n");
    }
    @Option(name="-homeDir",usage="Root directory of the plugin folder.  This serves as the root directory of the PluginManager.")
    public String homeDir = null;
    
    @Option(name="-asciiDest",usage="Full path of the location to save the steps asciidoc.  Defaults to ./allAscii")
    public String asciiDest = null;

    @Option(name="-declarativeDest",usage="Full path of the location to save the Declarative asciidoc. Defaults to ./declarative")
    public String declarativeDest = null;

    public static void main(String[] args){
        PipelineStepExtractor pse = new PipelineStepExtractor();
        try{
            CmdLineParser p = new CmdLineParser(pse);
            p.parseArgument(args);
        } catch(Exception ex){
            LOG.log(Level.SEVERE, "There was an error with parsing the commands, defaulting to the home directory.", ex);
        }
        try{
            Map<String, Map<String, List<QuasiDescriptor>>> steps = pse.findSteps();
            pse.generateAscii(steps, pse.pluginManager);
            pse.generateDeclarativeAscii();
        } catch(Exception ex){
            LOG.log(Level.SEVERE, "Error in finding all the steps", ex);
        }
        LOG.info("CONVERSION COMPLETE!");
        System.exit(0); //otherwise environment hangs around
    }

    public HyperLocalPluginManger pluginManager;

    public Map<String, Map<String, List<QuasiDescriptor>>> findSteps(){
        Map<String, Map<String, List<QuasiDescriptor>>> completeListing = new HashMap<>();
        try {
            //setup
            if(homeDir == null){
                pluginManager = new HyperLocalPluginManger(false);
            } else {
                pluginManager = new HyperLocalPluginManger(homeDir, false);
            }

            // Set up mocks
            Jenkins.JenkinsHolder mockJenkinsHolder = mock(Jenkins.JenkinsHolder.class);
            MockJenkins mJ = new MockJenkins();
            Jenkins mockJenkins = mJ.getMockJenkins(pluginManager);
            when(mockJenkinsHolder.getInstance()).thenReturn(mockJenkins);

            java.lang.reflect.Field jenkinsHolderField = Jenkins.class.getDeclaredField("HOLDER");
            jenkinsHolderField.setAccessible(true);
            jenkinsHolderField.set(null, mockJenkinsHolder);

            InitStrategy initStrategy = new InitStrategy();
            executeReactor(initStrategy, pluginManager.diagramPlugins(initStrategy));
            List<StepDescriptor> steps = pluginManager.getPluginStrategy().findComponents(StepDescriptor.class);

            //gather current and depricated steps
            Map<String, List<QuasiDescriptor>> required = processSteps(false, steps);
            Map<String, List<QuasiDescriptor>> optional = processSteps(true, steps);
            
            for(String req : required.keySet()){
                Map<String, List<QuasiDescriptor>> newList = new HashMap<>();
                newList.put("Steps", required.get(req));
                completeListing.put(req, newList);
            }
            for(String opt : optional.keySet()){
                Map<String, List<QuasiDescriptor>> exists = completeListing.get(opt);
                if(exists == null){
                    exists = new HashMap<>();
                }
                exists.put("Advanced/Deprecated Steps",optional.get(opt));
                completeListing.put(opt, exists);
            }
        } catch (Exception ex){
            LOG.log(Level.SEVERE, "Step generation failed", ex);
        }

        return completeListing;
    }

    private Map<String, List<QuasiDescriptor>> processSteps(boolean optional, List<StepDescriptor> steps) {
        Map<String, List<QuasiDescriptor>> required = new HashMap<>();
        for (StepDescriptor d : getStepDescriptors(optional, steps)) {
            String pluginName = pluginManager.getPluginNameForDescriptor(d);
            required.computeIfAbsent(pluginName, k -> new ArrayList<>())
                .add(new QuasiDescriptor(d, null));
            getMetaDelegates(d).forEach(delegateDescriptor -> {
                 String nestedPluginName = pluginManager.getPluginNameForDescriptor(delegateDescriptor);
                 required.computeIfAbsent(nestedPluginName, k -> new ArrayList<>())
                         .add(new QuasiDescriptor(delegateDescriptor, d));
            }); // TODO currently not handling metasteps with other parameters, either required or (like GenericSCMStep) not
        }
        return required;
    }

    protected static Stream<Descriptor<?>> getMetaDelegates(Descriptor<?> d) {
        if (d instanceof StepDescriptor && ((StepDescriptor) d).isMetaStep()) {
            DescribableModel<?> m = DescribableModel.of(d.clazz);
            Collection<DescribableParameter> parameters = m.getParameters();
            if (parameters.size() == 1) {
                DescribableParameter delegate = parameters.iterator().next();
                if (delegate.isRequired()) {
                    if (delegate.getType() instanceof HeterogeneousObjectType) {
                        return ((HeterogeneousObjectType) delegate.getType()).getTypes()
                                .values().stream().map(PipelineStepExtractor::getDescriptor);
                    }
                }
            }
        }
        return Stream.empty();
    }

    private static Descriptor<?> getDescriptor(DescribableModel<?> delegateOptionSchema) {
        Class<?> delegateOptionType = delegateOptionSchema.getType();
        return Jenkins.get().getDescriptor(delegateOptionType.asSubclass(Describable.class));
    }

    /**
     * Executes a reactor.
     *
     * @param is
     *      If non-null, this can be consulted for ignoring some tasks. Only used during the initialization of Jenkins.
     */
    private void executeReactor(final InitStrategy is, TaskBuilder... builders) throws IOException, InterruptedException, ReactorException {
        Reactor reactor = new Reactor(builders) {
            /**
             * Sets the thread name to the task for better diagnostics.
             */
            @Override
            protected void runTask(Task task) throws Exception {
                if (is!=null && is.skipInitTask(task))  return;

                String taskName = task.getDisplayName();

                Thread t = Thread.currentThread();
                String name = t.getName();

                try (ACLContext context = ACL.as2(ACL.SYSTEM2)) { // full access in the initialization thread
                    if (taskName !=null) {
                        t.setName(taskName);
                    }
                    super.runTask(task);
                } finally {
                    t.setName(name);
                }
            }
        };

        new InitReactorRunner() {
            @Override
            protected void onInitMilestoneAttained(InitMilestone milestone) {
                LOG.info("init milestone attained " + milestone);
            }
        }.run(reactor);
    }

    public Collection<? extends StepDescriptor> getStepDescriptors(boolean advanced, List<StepDescriptor> all) {
        TreeSet<StepDescriptor> t = new TreeSet<>(new StepDescriptorComparator());
        for (StepDescriptor d : all) {
            if (d.isAdvanced() == advanced) {
                t.add(d);
            }
        }
        return t;
    }

    private static class StepDescriptorComparator implements Comparator<StepDescriptor>, Serializable {
        @Override
        public int compare(StepDescriptor o1, StepDescriptor o2) {
            return o1.getFunctionName().compareTo(o2.getFunctionName());
        }
        private static final long serialVersionUID = 1L;
    }

    public void generateAscii(Map<String, Map<String, List<QuasiDescriptor>>> allSteps, PluginManager pluginManager){
        File allAscii;
        if (asciiDest != null) {
            allAscii = new File(asciiDest);
        } else {
            allAscii = new File("allAscii");
        }
        allAscii.mkdirs();
        String allAsciiPath = allAscii.getAbsolutePath();
        for(String plugin : allSteps.keySet()){
            LOG.info("processing " + plugin);
            Map<String, List<QuasiDescriptor>> byPlugin = allSteps.get(plugin);
            PluginWrapper thePlugin = pluginManager.getPlugin(plugin);
            String displayName = thePlugin == null ? "Jenkins Core" : thePlugin.getDisplayName();
            String whole9yards = ToAsciiDoc.generatePluginHelp(plugin, displayName, byPlugin, true);

            try {
                FileUtils.writeStringToFile(new File(allAsciiPath, plugin + ".adoc"), whole9yards, StandardCharsets.UTF_8);
            } catch (Exception ex){
                LOG.log(Level.SEVERE, "Error generating plugin file for " + plugin + ".  Skip.", ex);
                //continue to next plugin
            }
        }
    }

    public void generateDeclarativeAscii() {
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
                pluginDescMap = processDescriptors(d, pluginDescMap, filter);
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
                                                             @CheckForNull Predicate<Descriptor> filter) {
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

    private static final List<String> blockedOptionSteps = Arrays.asList("node", "stage", "withEnv", "script", "withCredentials");
    private static final List<Class<?>> blockedOptionStepContexts = Arrays.asList(Launcher.class, FilePath.class, Computer.class);

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
