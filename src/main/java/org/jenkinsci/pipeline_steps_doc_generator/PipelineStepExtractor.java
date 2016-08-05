package org.jenkinsci.pipeline_steps_doc_generator;

import hudson.init.InitMilestone;
import hudson.init.InitStrategy;
import hudson.security.ACL;
import jenkins.InitReactorRunner;
import org.acegisecurity.context.SecurityContextHolder;
import org.apache.commons.io.FileUtils;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jvnet.hudson.reactor.*;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;


/**
 * Process and find all the Pipeline steps definied in Jenkins plugins.
 */
public class PipelineStepExtractor {
    @Option(name="-homeDir",usage="Root directory of the plugin folder.  This serves as the root directory of the PluginManager.")
    public String homeDir = null;
    
    @Option(name="-asciiDest",usage="Full path of the location to save the asciidoc.  Defaults to ./allAscii")
    public String asciiDest = null;
    
    public static void main(String[] args){
        PipelineStepExtractor pse = new PipelineStepExtractor();
        try{
            CmdLineParser p = new CmdLineParser(pse);
            p.parseArgument(args);
        } catch(Exception ex){
            System.out.println("There was an error with parsing the commands, defaulting to the home directory.");
        }
        try{
            pse.generateAscii(pse.findSteps());
        } catch(Exception ex){
            System.out.println("Error in finding all the steps");
        }
        System.out.println("CONVERSION COMPLETE!");
        System.exit(0); //otherwise environment hangs around
    }

    public Map<String, Map<String, List<StepDescriptor>>> findSteps(){
        Map<String, Map<String, List<StepDescriptor>>> completeListing = new HashMap<String, Map<String, List<StepDescriptor>>>();
        try {
            //setup
            HyperLocalPluginManger spm;
            if(homeDir == null){
                spm = new HyperLocalPluginManger(false);
            } else {
                spm = new HyperLocalPluginManger(homeDir, false);
            }
            InitStrategy initStrategy = new InitStrategy();
            executeReactor(initStrategy, spm.diagramPlugins(initStrategy));
            List<StepDescriptor> steps = spm.getPluginStrategy().findComponents(StepDescriptor.class, spm.uberPlusClassLoader);
            Map<String, String> stepsToPlugin = spm.uberPlusClassLoader.getByPlugin();

            //gather current and depricated steps
            Map<String, List<StepDescriptor>> required = processSteps(false, steps, stepsToPlugin);
            Map<String, List<StepDescriptor>> optional = processSteps(true, steps, stepsToPlugin);
            
            for(String req : required.keySet()){
                Map<String, List<StepDescriptor>> newList = new HashMap<String, List<StepDescriptor>>();
                newList.put("Steps", required.get(req));
                completeListing.put(req, newList);
            }
            for(String opt : optional.keySet()){
                Map<String, List<StepDescriptor>> exists = completeListing.get(opt);
                if(exists == null){
                    exists = new HashMap<String, List<StepDescriptor>>();
                }
                exists.put("Advanced/Deprecated Steps",optional.get(opt));
                completeListing.put(opt, exists);
            }
        } catch (Exception ex){
            //log exception, job essentially fails
        }

        return completeListing;
    }

    private Map<String, List<StepDescriptor>> processSteps(boolean optional, List<StepDescriptor> steps, Map<String, String> stepsToPlugin){
        Map<String, List<StepDescriptor>> required = new HashMap<String, List<StepDescriptor>>();
        for (StepDescriptor d : getStepDescriptors(optional, steps)) {
            if(stepsToPlugin.get(d.getClass().getName()) != null){
                String pluginName = stepsToPlugin.get(d.getClass().getName()).trim();
                List<StepDescriptor> allSteps = required.get(pluginName);
                if(allSteps == null){
                    allSteps = new ArrayList<StepDescriptor>();
                }
                allSteps.add(d);
                required.put(pluginName, allSteps);
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

    public void generateAscii(Map<String, Map<String, List<StepDescriptor>>> allSteps){
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
            Map<String, List<StepDescriptor>> byPlugin = allSteps.get(plugin);
            String whole9yards = ToAsciiDoc.generatePluginHelp(plugin, byPlugin, true); 
            
            try{
                FileUtils.writeStringToFile(new File(allAsciiPath, plugin + ".adoc"), whole9yards, StandardCharsets.UTF_8);
            } catch (Exception ex){
                System.out.println("Error generating plugin file for " + plugin + ".  Skip.");
                //continue to next plugin
            }
        }
    }
}
