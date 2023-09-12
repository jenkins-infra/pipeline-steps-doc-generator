package org.jenkinsci.pipeline_steps_doc_generator;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import hudson.MockJenkins;
import hudson.PluginWrapper;
import hudson.init.InitMilestone;
import hudson.init.InitStrategy;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.security.ACL;
import hudson.security.ACLContext;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;
import jenkins.InitReactorRunner;
import jenkins.model.Jenkins;
import org.jenkinsci.infra.tools.HyperLocalPluginManager;
import org.jenkinsci.plugins.structs.describable.DescribableModel;
import org.jenkinsci.plugins.structs.describable.DescribableParameter;
import org.jenkinsci.plugins.structs.describable.HeterogeneousObjectType;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.json.JSONObject;
import org.jvnet.hudson.reactor.Reactor;
import org.jvnet.hudson.reactor.ReactorException;
import org.jvnet.hudson.reactor.Task;
import org.jvnet.hudson.reactor.TaskBuilder;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

/**
 * Process and find all the Pipeline steps definied in Jenkins plugins.
 */
public class PipelineStepExtractor {
    private static final Logger LOG = Logger.getLogger(PipelineStepExtractor.class.getName());

    static {
        System.setProperty("java.util.logging.SimpleFormatter.format", "[%4$-7s] %5$s %6$s%n");
    }

    @Option(
            name = "-homeDir",
            usage = "Root directory of the plugin folder.  This serves as the root directory of the PluginManager.")
    public String homeDir = null;

    @Option(
            name = "-asciiDest",
            usage = "Full path of the location to save the steps asciidoc.  Defaults to ./allAscii")
    public String asciiDest = null;

    @Option(
            name = "-declarativeDest",
            usage = "Full path of the location to save the Declarative asciidoc. Defaults to ./declarative")
    public String declarativeDest = null;

    public static void main(String[] args) {
        PipelineStepExtractor pse = new PipelineStepExtractor();
        try {
            CmdLineParser p = new CmdLineParser(pse);
            p.parseArgument(args);
        } catch (Exception ex) {
            LOG.log(
                    Level.SEVERE,
                    "There was an error with parsing the commands, defaulting to the home directory.",
                    ex);
        }
        try {
            Map<String, Map<String, List<QuasiDescriptor>>> steps = pse.findSteps();
            pse.generateAscii(steps, pse.pluginManager);
            pse.generateDeclarativeSteps();
        } catch (Exception ex) {
            LOG.log(Level.SEVERE, "Error in finding all the steps", ex);
        }
        LOG.info("CONVERSION COMPLETE!");
        System.exit(0); // otherwise environment hangs around
    }

    public HyperLocalPluginManager pluginManager;

    public Map<String, Map<String, List<QuasiDescriptor>>> findSteps() {
        Map<String, Map<String, List<QuasiDescriptor>>> completeListing = new HashMap<>();
        try {
            // setup
            if (homeDir == null) {
                pluginManager = new HyperLocalPluginManager(false);
            } else {
                pluginManager = new HyperLocalPluginManager(homeDir, false);
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

            // gather current and depricated steps
            Map<String, List<QuasiDescriptor>> required = processSteps(false, steps);
            Map<String, List<QuasiDescriptor>> optional = processSteps(true, steps);

            for (String req : required.keySet()) {
                Map<String, List<QuasiDescriptor>> newList = new HashMap<>();
                newList.put("Steps", required.get(req));
                completeListing.put(req, newList);
            }
            for (String opt : optional.keySet()) {
                Map<String, List<QuasiDescriptor>> exists = completeListing.get(opt);
                if (exists == null) {
                    exists = new HashMap<>();
                }
                exists.put("Advanced/Deprecated Steps", optional.get(opt));
                completeListing.put(opt, exists);
            }
        } catch (Exception ex) {
            LOG.log(Level.SEVERE, "Step generation failed", ex);
        }

        return completeListing;
    }

    private Map<String, List<QuasiDescriptor>> processSteps(boolean optional, List<StepDescriptor> steps) {
        Map<String, List<QuasiDescriptor>> required = new HashMap<>();
        for (StepDescriptor d : getStepDescriptors(optional, steps)) {
            String pluginName = pluginManager.getPluginNameForDescriptor(d);
            required.computeIfAbsent(pluginName, k -> new ArrayList<>()).add(new QuasiDescriptor(d, null));
            getMetaDelegates(d).forEach(delegateDescriptor -> {
                String nestedPluginName = pluginManager.getPluginNameForDescriptor(delegateDescriptor);
                required.computeIfAbsent(nestedPluginName, k -> new ArrayList<>())
                        .add(new QuasiDescriptor(delegateDescriptor, d));
            }); // TODO currently not handling metasteps with other parameters, either required or (like GenericSCMStep)
            // not
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
                        return ((HeterogeneousObjectType) delegate.getType())
                                .getTypes().values().stream().map(PipelineStepExtractor::getDescriptor);
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
    private void executeReactor(final InitStrategy is, TaskBuilder... builders)
            throws IOException, InterruptedException, ReactorException {
        Reactor reactor = new Reactor(builders) {
            /**
             * Sets the thread name to the task for better diagnostics.
             */
            @Override
            protected void runTask(Task task) throws Exception {
                if (is != null && is.skipInitTask(task)) return;

                String taskName = task.getDisplayName();

                Thread t = Thread.currentThread();
                String name = t.getName();

                try (ACLContext context = ACL.as2(ACL.SYSTEM2)) { // full access in the initialization thread
                    if (taskName != null) {
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

    public void generateAscii(Map<String, Map<String, List<QuasiDescriptor>>> allSteps, HyperLocalPluginManager pluginManager) {
        File allAscii;
        if (asciiDest != null) {
            allAscii = new File(asciiDest);
        } else {
            allAscii = new File("allAscii");
        }

        JSONObject deprecatedPlugins = new JSONObject();
        try {
            deprecatedPlugins = new JSONObject(new String(
                            new URL("https://updates.jenkins.io/current/update-center.actual.json")
                                    .openStream()
                                    .readAllBytes(),
                            StandardCharsets.UTF_8))
                    .getJSONObject("deprecations");

        } catch (IOException ex) {
            LOG.log(Level.WARNING, "Update center could not be read" + ex);
        }

        allAscii.mkdirs();
        String allAsciiPath = allAscii.getAbsolutePath();

        ToAsciiDoc toAsciiDoc = new ToAsciiDoc(pluginManager);
        for (String plugin : allSteps.keySet()) {
            LOG.info("processing " + plugin);
            Map<String, List<QuasiDescriptor>> byPlugin = allSteps.get(plugin);
            PluginWrapper thePlugin = pluginManager.getPlugin(plugin);
            String displayName = thePlugin == null ? "Jenkins Core" : thePlugin.getDisplayName();
            boolean isDeprecated = deprecatedPlugins.has(plugin);
            String whole9yards = toAsciiDoc.generatePluginHelp(plugin, displayName, byPlugin, isDeprecated,
                    true);

            try {
                Paths.get(allAsciiPath, plugin).toFile().mkdirs();
                Files.writeString(
                        new File(allAsciiPath, plugin + "/index.adoc").toPath(), whole9yards, StandardCharsets.UTF_8);
            } catch (Exception ex) {
                LOG.log(Level.SEVERE, "Error generating plugin file for " + plugin + ".  Skip.", ex);
                // continue to next plugin
            }
        }
        for (Map.Entry<String, StringBuilder> entry: toAsciiDoc.getExtractedParams().entrySet()) {
            String plugin = entry.getKey();
            try {
                Paths.get(allAsciiPath, plugin).toFile().mkdirs();
                Files.writeString(
                        new File(allAsciiPath, entry.getKey() + "/params.adoc").toPath(), entry.getValue().toString(), StandardCharsets.UTF_8);
            } catch (IOException ex) {
                LOG.log(Level.SEVERE, "Error generating plugin params file for " + entry.getKey() + ".  Skip.", ex);
            }
        }
    }

    public void generateDeclarativeSteps() {
        DeclarativeSteps ds = new DeclarativeSteps();
        ds.generateDeclarativeAscii(declarativeDest, pluginManager);
    }
}
