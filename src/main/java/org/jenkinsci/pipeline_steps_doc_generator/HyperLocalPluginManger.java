package org.jenkinsci.pipeline_steps_doc_generator;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import hudson.ClassicPluginStrategy;
import hudson.Extension;
import hudson.ExtensionComponent;
import hudson.ExtensionFinder;
import hudson.LocalPluginManager;
import hudson.PluginManager;
import hudson.PluginStrategy;
import hudson.PluginWrapper;
import hudson.Util;
import hudson.init.InitMilestone;
import hudson.init.InitStrategy;
import hudson.model.Hudson;
import hudson.security.ACL;
import hudson.util.CyclicGraphDetector;
import jenkins.ClassLoaderReflectionToolkit;
import jenkins.ExtensionComponentSet;
import jenkins.ExtensionFilter;
import jenkins.InitReactorRunner;
import net.java.sezpoz.Index;
import net.java.sezpoz.IndexItem;
import org.acegisecurity.context.SecurityContextHolder;
import org.apache.tools.ant.filters.StringInputStream;
import org.jvnet.hudson.reactor.*;

import static hudson.init.InitMilestone.*;
import static java.util.logging.Level.WARNING;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.ref.WeakReference;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;

/**
 * Acts as a PluginManager that operates outside the normal startup process of Jenkins.  Essentially, this captures the
 * loading ability of the PluginManager without actually starting those plugins.  This means it runs independently of
 * Jenkins.
 *
 * Since this PluginManager operates on such a local scale, many classes associated with it are also changed to not
 * use calls to Jenkins.
 */
public class HyperLocalPluginManger extends LocalPluginManager{
    private final ModClassicPluginStrategy strategy;
    public final UberPlusClassLoader uberPlusClassLoader = new UberPlusClassLoader();
    private final boolean checkCycles;

    public HyperLocalPluginManger(){
        this(".");
    }
    
    public HyperLocalPluginManger(boolean cycles){
        this(".", cycles);
    }
    
    public HyperLocalPluginManger(String rootDir) {
        super(new File(rootDir));
        this.strategy = createModPluginStrategy();
        checkCycles = true;
    }
    public HyperLocalPluginManger(String rootDir, boolean cycles) {
        super(new File(rootDir));
        this.strategy = createModPluginStrategy();
        checkCycles = cycles;
    }

    @Override
    public ModClassicPluginStrategy getPluginStrategy() {
        return strategy;
    }

    protected ModClassicPluginStrategy createModPluginStrategy() {
        return new ModClassicPluginStrategy(this);
    }

    /**
     * Import plugins for use.
     *
     * Note: the plugin cycles section is optional because, while incredibly helpful, 
     * it can easily cross the open file descriptors threshold crippling any other work 
     * when processing a huge amount of plugins (several hundred).
     */
    public TaskBuilder diagramPlugins(final InitStrategy initStrategy){
        return new TaskGraphBuilder() {
            List<File> archives;

            {
                Handle listUpPlugins = add("Listing up plugins", new Executable() {
                    public void run(Reactor session) throws Exception {
                        archives = initStrategy.listPluginArchives(HyperLocalPluginManger.this);
                    }
                });

                requires(listUpPlugins).attains(PLUGINS_LISTED).add("Preparing plugins",new Executable() {
                    public void run(Reactor session) throws Exception {
                        // once we've listed plugins, we can fill in the reactor with plugin-specific initialization tasks
                        TaskGraphBuilder g = new TaskGraphBuilder();

                        final Map<String,File> inspectedShortNames = new HashMap<String,File>();

                        for( final File arc : archives ) {
                            g.followedBy().notFatal().attains(PLUGINS_LISTED).add("Inspecting plugin " + arc, new Executable() {
                                public void run(Reactor session1) throws Exception {
                                    try {
                                        PluginWrapper p = strategy.createPluginWrapper(arc);
                                        if (isDuplicate(p)) return;

                                        //p.isBundled = false;  //flying blind here; luckily doesn't look used
                                        plugins.add(p);
                                        
                                        if(p.isActive()) //omg test!
                                            activePlugins.add(p);
                                    } catch (IOException e) {
                                        failedPlugins.add(new FailedPlugin(arc.getName(),e));
                                        throw e;
                                    }
                                }

                                /**
                                 * Inspects duplication. this happens when you run hpi:run on a bundled plugin,
                                 * as well as putting numbered jpi files, like "cobertura-1.0.jpi" and "cobertura-1.1.jpi"
                                 */
                                private boolean isDuplicate(PluginWrapper p) {
                                    String shortName = p.getShortName();
                                    if (inspectedShortNames.containsKey(shortName)) {
                                        System.out.println("Ignoring "+arc+" because "+inspectedShortNames.get(shortName)+" is already loaded");
                                        return true;
                                    }

                                    inspectedShortNames.put(shortName,arc);
                                    return false;
                                }
                            });
                        }

                        if(checkCycles){
                            g.followedBy().attains(PLUGINS_LISTED).add("Checking cyclic dependencies", new Executable() {
                                /**
                                 * Makes sure there's no cycle in dependencies.
                                 */
                                public void run(Reactor reactor) throws Exception {
                                    try {
                                        CyclicGraphDetector<PluginWrapper> cgd = new CyclicGraphDetector<PluginWrapper>() {
                                            @Override
                                            protected List<PluginWrapper> getEdges(PluginWrapper p) {
                                                List<PluginWrapper> next = new ArrayList<PluginWrapper>();
                                                addTo(p.getDependencies(), next);
                                                addTo(p.getOptionalDependencies(), next);
                                                return next;
                                            }

                                            private void addTo(List<PluginWrapper.Dependency> dependencies, List<PluginWrapper> r) {
                                                for (PluginWrapper.Dependency d : dependencies) {
                                                    PluginWrapper p = getPlugin(d.shortName);
                                                    if (p != null)
                                                        r.add(p);
                                                }
                                            }

                                            @Override
                                            protected void reactOnCycle(PluginWrapper q, List<PluginWrapper> cycle)
                                                    throws hudson.util.CyclicGraphDetector.CycleDetectedException {

                                                System.out.println("FATAL: found cycle in plugin dependencies: (root="+q+", deactivating all involved) "+Util.join(cycle," -> "));
                                                for (PluginWrapper pluginWrapper : cycle) {
                                                    pluginWrapper.setHasCycleDependency(true);
                                                    failedPlugins.add(new FailedPlugin(pluginWrapper.getShortName(), new CycleDetectedException(cycle)));
                                                }
                                            }

                                        };
                                        cgd.run(getPlugins());

                                        // obtain topologically sorted list and overwrite the list
                                        ListIterator<PluginWrapper> litr = getPlugins().listIterator();
                                        for (PluginWrapper p : cgd.getSorted()) {
                                            litr.next();
                                            litr.set(p);
                                            if(p.isActive())
                                                activePlugins.add(p);
                                        }
                                    } catch (CyclicGraphDetector.CycleDetectedException e) {
                                        stop(); // disable all plugins since classloading from them can lead to StackOverflow
                                        throw e;    // let load fail
                                    }
                                }
                            });
                        }

                        session.addAll(g.discoverTasks(session));
                    }
                });
            }
        };
    }

    /**
     * {@link ClassLoader} that can see all plugins.
     */
    public final class UberPlusClassLoader extends ClassLoader {
        /**
         * Make generated types visible.
         * Keyed by the generated class name.
         */
        private ConcurrentMap<String, WeakReference<Class>> generatedClasses = new ConcurrentHashMap<String, WeakReference<Class>>();
        /** Cache of loaded, or known to be unloadable, classes. */
        private final Map<String,Class<?>> loaded = new HashMap<String,Class<?>>();
        private final Map<String, String> byPlugin = new HashMap<String, String>();

        public UberPlusClassLoader() {
            super(PluginManager.class.getClassLoader());
        }

        public void addNamedClass(String className, Class c) {
            generatedClasses.put(className,new WeakReference<Class>(c));
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            //likely want to avoid this, but we'll deal with it right now.
            WeakReference<Class> wc = generatedClasses.get(name);
            if (wc!=null) {
                Class c = wc.get();
                if (c!=null)    return c;
                else            generatedClasses.remove(name,wc);
            }

            if (name.startsWith("SimpleTemplateScript")) { // cf. groovy.text.SimpleTemplateEngine
                throw new ClassNotFoundException("ignoring " + name);
            }
            synchronized (loaded) {
                if (loaded.containsKey(name)) {
                    Class<?> c = loaded.get(name);
                    if (c != null) {
                        return c;
                    } else {
                        throw new ClassNotFoundException("cached miss for " + name);
                    }
                }
            }
            if (FAST_LOOKUP) {
                for (PluginWrapper p : activePlugins) {
                    try {
                        Class<?> c = ClassLoaderReflectionToolkit._findLoadedClass(p.classLoader, name);
                        if (c != null) {
                            synchronized (loaded) {
                                loaded.put(name, c);
                            }
                            synchronized (byPlugin){
                                byPlugin.put(c.getName(), p.getShortName());
                            }
                            return c;
                        }
                        // calling findClass twice appears to cause LinkageError: duplicate class def
                        c = ClassLoaderReflectionToolkit._findClass(p.classLoader, name);
                        synchronized (loaded) {
                            loaded.put(name, c);
                        }
                        synchronized (byPlugin){
                            byPlugin.put(c.getName(), p.getShortName());
                        }
                        return c;
                    } catch (ClassNotFoundException e) {
                        //not found. try next
                    }
                }
            } else {
                for (PluginWrapper p : activePlugins) {
                    try {
                        Class c = p.classLoader.loadClass(name);
                        synchronized (byPlugin){
                            byPlugin.put(c.getName(), p.getShortName());
                        }
                        return c;
                    } catch (ClassNotFoundException e) {
                        //not found. try next
                    }
                }
            }
            synchronized (loaded) {
                loaded.put(name, null);
            }
            // not found in any of the classloader. delegate.
            throw new ClassNotFoundException(name);
        }

        @Override
        protected URL findResource(String name) {
            if (FAST_LOOKUP) {
                for (PluginWrapper p : activePlugins) {
                    URL url = ClassLoaderReflectionToolkit._findResource(p.classLoader, name);
                    if(url!=null)
                        return url;
                }
            } else {
                for (PluginWrapper p : activePlugins) {
                    URL url = p.classLoader.getResource(name);
                    if(url!=null)
                        return url;
                }
            }
            return null;
        }

        @Override
        protected Enumeration<URL> findResources(String name) throws IOException {
            List<URL> resources = new ArrayList<URL>();
            if (FAST_LOOKUP) {
                for (PluginWrapper p : activePlugins) {
                    resources.addAll(Collections.list(ClassLoaderReflectionToolkit._findResources(p.classLoader, name)));
                }
            } else {
                for (PluginWrapper p : activePlugins) {
                    resources.addAll(Collections.list(p.classLoader.getResources(name)));
                }
            }
            return Collections.enumeration(resources);
        }

        public Map<String, String> getByPlugin(){
            return byPlugin;
        }

        @Override
        public String toString() {
            // only for debugging purpose
            return "classLoader " +  getClass().getName();
        }
    }


    /**
     * A PluginStrategy that supports custom classloaders (the UberPlusClassLoader).
     */
    public class ModClassicPluginStrategy extends ClassicPluginStrategy {
        private ClassLoader classLoader;

        public ModClassicPluginStrategy(HyperLocalPluginManger pluginManager) {
            super(pluginManager);
            classLoader = pluginManager.uberPlusClassLoader;
        }

        public <T> List<ExtensionComponent<T>> findComponents(Class<T> type, Hudson hudson) {
            List<SmallSezpoz> finders = Collections.<SmallSezpoz>singletonList(new SmallSezpoz());
            for (SmallSezpoz finder : finders) {
                finder.scout(type, classLoader);
            }

            List<ExtensionComponent<T>> r = Lists.newArrayList();
            for (SmallSezpoz finder : finders) {	
                try {
                    r.addAll(finder.find(type, classLoader));
                } catch (AbstractMethodError e) {
                    // backward compatibility
                    //nothing actually happens here...
                }
            }

            List<ExtensionComponent<T>> filtered = Lists.newArrayList();
            for (ExtensionComponent<T> e : r) {
                if (ExtensionFilter.isAllowed(type, e))
                    filtered.add(e);
            }

            return filtered;
        }
        
        public <T> List<T> findComponents(Class<T> type) {
            List<SmallSezpoz> finders = Collections.<SmallSezpoz>singletonList(new SmallSezpoz());
            for (SmallSezpoz finder : finders) {
                finder.scout(type, classLoader);
            }

            List<ExtensionComponent<T>> r = Lists.newArrayList();
            for (SmallSezpoz finder : finders) {
                try {
                    r.addAll(finder.find(type, classLoader));
                } catch (AbstractMethodError e) {
                    // backward compatibility
                    //nothing actually happens here...
                }
            }

            List<T> filtered = Lists.newArrayList();
            for (ExtensionComponent<T> e : r) {
                if (ExtensionFilter.isAllowed(type, e))
                    filtered.add(e.getInstance());
            }

            return filtered;
        }
    }

    /**
     * This is pretty much a copy of the final ExtensionFinder.Sezpoz class from 1.651 fitted
     * for a custom ClassLoader rather than checking Jenkins
     * The only differences are:
     *   * getIndices -> ClassLoader parameter; doesn't check Jenkins
     *   * find -> ClassLoader parameter
     *   * scout -> ClassLoader parmeter
     *
     * IMPORTANT: don't use find(Class<T> type, Hudson hud) as the getIndices method will error.
     */
    public static final class SmallSezpoz extends ExtensionFinder {

        private volatile List<IndexItem<Extension,Object>> indices;

        private List<IndexItem<Extension,Object>> getIndices(ClassLoader cl) {
            if (indices==null) {
                indices = ImmutableList.copyOf(Index.load(Extension.class, Object.class, cl));
            }
            return indices;
        }

        /**
         * Required as part of ExtensionFinder
         */
        @Override
        public synchronized ExtensionComponentSet refresh() {
            return ExtensionComponentSet.EMPTY; // we haven't loaded anything
        }

        /**
         * DO NOT EVER CALL (unless called after other find)
         * 
         * This was required for overriding ExtensionFinder
         */
        public <T> Collection<ExtensionComponent<T>> find(Class<T> type, Hudson hud) {
            return _find(type,getIndices(null));
        }

        public <T> Collection<ExtensionComponent<T>> find(Class<T> type, ClassLoader cl) {
            return _find(type,getIndices(cl));
        }

        /**
         * Finds all the matching {@link IndexItem}s that match the given type and instantiate them.
         */
        private <T> Collection<ExtensionComponent<T>> _find(Class<T> type, List<IndexItem<Extension,Object>> indices) {
            List<ExtensionComponent<T>> result = new ArrayList<>();

            for (IndexItem<Extension,Object> item : indices) {  
                try {
                    AnnotatedElement e = item.element();
                    Class<?> extType;
                    if (e instanceof Class) {
                        extType = (Class) e;
                    } else
                    if (e instanceof Field) {
                        extType = ((Field)e).getType();
                    } else
                    if (e instanceof Method) {
                        extType = ((Method)e).getReturnType();
                    } else
                        throw new AssertionError();

                    if(type.isAssignableFrom(extType)) {
                        Object instance = item.instance();
                        if(instance!=null)
                            result.add(new ExtensionComponent<>(type.cast(instance),item.annotation()));
                    }
                } catch (LinkageError|Exception e) {
                    // sometimes the instantiation fails in an indirect classloading failure,
                    // which results in a LinkageError
                    System.out.println("Failed to load "+item.className() + "\n" +  e);
                }
            }

            return result;
        }

        public void scout(Class extensionType, ClassLoader cl) {
            for (IndexItem<Extension,Object> item : getIndices(cl)) {
                try {
                    AnnotatedElement e = item.element();
                    Class<?> extType;
                    if (e instanceof Class) {
                        extType = (Class) e;
                    } else
                    if (e instanceof Field) {
                        extType = ((Field)e).getType();
                    } else
                    if (e instanceof Method) {
                        extType = ((Method)e).getReturnType();
                    } else
                        throw new AssertionError();
                    // according to JDK-4993813 this is the only way to force class initialization
                    Class.forName(extType.getName(),true,extType.getClassLoader());
                } catch (Exception | LinkageError e) {
                    System.out.println("Failed to scout " + item.className() + "\n" + e);
                }
            }
        }

        private Level logLevel(IndexItem<Extension, Object> item) {
            return item.annotation().optional() ? Level.FINE : Level.WARNING;
        }
    }

}

