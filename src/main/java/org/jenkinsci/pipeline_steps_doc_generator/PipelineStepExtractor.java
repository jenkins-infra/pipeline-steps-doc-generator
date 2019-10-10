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
import hudson.triggers.TriggerDescriptor;
import jenkins.InitReactorRunner;
import jenkins.model.Jenkins;
import org.acegisecurity.context.SecurityContextHolder;
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

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
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
import java.util.stream.Collectors;

import static org.mockito.Mockito.*;

/**
 * Process and find all the Pipeline steps definied in Jenkins plugins.
 */
public class PipelineStepExtractor {
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
            ex.printStackTrace();
            System.out.println("There was an error with parsing the commands, defaulting to the home directory.");
        }
        try{
            Map<String, Map<String, List<QuasiDescriptor>>> steps = pse.findSteps();
            pse.generateAscii(steps, pse.pluginManager);
            pse.generateDeclarativeAscii();
        } catch(Exception ex){
            ex.printStackTrace();
            System.out.println("Error in finding all the steps");
        }
        System.out.println("CONVERSION COMPLETE!");
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
            Map<String, String> stepsToPlugin = pluginManager.uberPlusClassLoader.getByPlugin();

            //gather current and depricated steps
            Map<String, List<QuasiDescriptor>> required = processSteps(false, steps, stepsToPlugin);
            Map<String, List<QuasiDescriptor>> optional = processSteps(true, steps, stepsToPlugin);
            
            for(String req : required.keySet()){
                Map<String, List<QuasiDescriptor>> newList = new HashMap<String, List<QuasiDescriptor>>();
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
            ex.printStackTrace();
            //log exception, job essentially fails
        }

        return completeListing;
    }

    private Map<String, List<QuasiDescriptor>> processSteps(boolean optional, List<StepDescriptor> steps, Map<String, String> stepsToPlugin) {
        Map<String, List<QuasiDescriptor>> required = new HashMap<>();
        for (StepDescriptor d : getStepDescriptors(optional, steps)) {
            try {
                pluginManager.uberPlusClassLoader.findClass(d.getClass().getName());
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            }
            if(stepsToPlugin.get(d.getClass().getName()) != null){
                String pluginName = stepsToPlugin.get(d.getClass().getName()).trim();
                List<QuasiDescriptor> allSteps = required.get(pluginName);
                if(allSteps == null){
                    allSteps = new ArrayList<>();
                    required.put(pluginName, allSteps);
                }
                allSteps.add(new QuasiDescriptor(d));
                if (d.isMetaStep()) {
                    DescribableModel<?> m = DescribableModel.of(d.clazz);
                    Collection<DescribableParameter> parameters = m.getParameters();
                    if (parameters.size() == 1) {
                        DescribableParameter delegate = parameters.iterator().next();
                        if (delegate.isRequired()) {
                            if (delegate.getType() instanceof HeterogeneousObjectType) {
                                for (DescribableModel<?> delegateOptionSchema : ((HeterogeneousObjectType) delegate.getType()).getTypes().values()) {
                                    Class<?> delegateOptionType = delegateOptionSchema.getType();
                                    Descriptor<?> delegateDescriptor = Jenkins.getInstance().getDescriptor(delegateOptionType.asSubclass(Describable.class));
                                    // TODO for some reason, stepsToPlugin contains entries for descriptors but not their describables:
                                    String nestedPluginName = stepsToPlugin.get(delegateDescriptor.getClass().getName());
                                    if (nestedPluginName == null) {
                                        nestedPluginName = "core";
                                    }
                                    Set<String> symbols = SymbolLookup.getSymbolValue(delegateDescriptor);
                                    if (!symbols.isEmpty()) {
                                        List<QuasiDescriptor> nestedSteps = required.get(nestedPluginName);
                                        if (nestedSteps == null) {
                                            nestedSteps = new ArrayList<>();
                                            required.put(nestedPluginName, nestedSteps);
                                        }
                                        nestedSteps.add(new QuasiDescriptor(delegateDescriptor));
                                    }
                                }
                            }
                        }
                    } // TODO currently not handling metasteps with other parameters, either required or (like GenericSCMStep) not
                }
            }
        }
        return required;
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

                ACL.impersonate(ACL.SYSTEM); // full access in the initialization thread
                String taskName = task.getDisplayName();

                Thread t = Thread.currentThread();
                String name = t.getName();
                if (taskName !=null)
                    t.setName(taskName);
                try {
                    long start = System.currentTimeMillis();
                    super.runTask(task);
                } finally {
                    t.setName(name);
                    SecurityContextHolder.clearContext();
                }
            }
        };

        new InitReactorRunner() {
            @Override
            protected void onInitMilestoneAttained(InitMilestone milestone) {
                System.out.println("init milestone attained " + milestone);
            }
        }.run(reactor);
    }

    public Collection<? extends StepDescriptor> getStepDescriptors(boolean advanced, List<StepDescriptor> all) {
        TreeSet<StepDescriptor> t = new TreeSet<StepDescriptor>(new StepDescriptorComparator());
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
        if(asciiDest != null){
            allAscii = new File(asciiDest);
        } else {
            allAscii = new File("allAscii");
        }
        allAscii.mkdirs();
        String allAsciiPath = allAscii.getAbsolutePath();
        for(String plugin : allSteps.keySet()){
            System.out.println("processing " + plugin);
            Map<String, List<QuasiDescriptor>> byPlugin = allSteps.get(plugin);
            PluginWrapper thePlugin = pluginManager.getPlugin(plugin);
            String whole9yards = ToAsciiDoc.generatePluginHelp(plugin, thePlugin == null ? "Core" : thePlugin.getDisplayName(), byPlugin, true);
            
            try{
                FileUtils.writeStringToFile(new File(allAsciiPath, plugin + ".adoc"), whole9yards, StandardCharsets.UTF_8);
            } catch (Exception ex){
                ex.printStackTrace();
                System.out.println("Error generating plugin file for " + plugin + ".  Skip.");
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
            System.out.println("Generating docs for directive " + entry.getKey());
            Map<String, List<Descriptor>> pluginDescMap = new HashMap<>();

            for (Class<? extends Descriptor> d : entry.getValue()) {
                Predicate<Descriptor> filter = filters.get(d);
                System.out.println(" - Loading descriptors of type " + d.getSimpleName() + " with filter: " + (filter != null));
                pluginDescMap = processDescriptors(d, pluginDescMap, filter);
            }

            String whole9yards = ToAsciiDoc.generateDirectiveHelp(entry.getKey(), pluginDescMap, true);

            try{
                FileUtils.writeStringToFile(new File(declPath, entry.getKey() + ".adoc"), whole9yards, StandardCharsets.UTF_8);
            } catch (Exception ex){
                ex.printStackTrace();
                System.out.println("Error generating directive file for " + entry.getKey() + ".  Skip.");
                //continue to next directive
            }
        }
    }

    private Map<String, List<Descriptor>> processDescriptors(@Nonnull Class<? extends Descriptor> c,
                                                             @Nonnull Map<String, List<Descriptor>> descMap,
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

        for (Descriptor d : descriptors.stream().filter(fullFilter).collect(Collectors.toList())) {
            String pluginName = descriptorsToPlugin.get(d.getClass().getName());
            if (pluginName != null) {
                pluginName = pluginName.trim();
            } else {
                pluginName = "core";
            }
            if (!descMap.containsKey(pluginName)) {
                descMap.put(pluginName, new ArrayList<>());
            }
            descMap.get(pluginName).add(d);
        }
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
