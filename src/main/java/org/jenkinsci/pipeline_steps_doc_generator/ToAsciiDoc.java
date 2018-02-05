package org.jenkinsci.pipeline_steps_doc_generator;

import hudson.model.Descriptor;
import org.jenkinsci.plugins.structs.SymbolLookup;
import org.jenkinsci.plugins.structs.describable.ArrayType;
import org.jenkinsci.plugins.structs.describable.AtomicType;
import org.jenkinsci.plugins.structs.describable.DescribableModel;
import org.jenkinsci.plugins.structs.describable.DescribableParameter;
import org.jenkinsci.plugins.structs.describable.EnumType;
import org.jenkinsci.plugins.structs.describable.ErrorType;
import org.jenkinsci.plugins.structs.describable.HeterogeneousObjectType;
import org.jenkinsci.plugins.structs.describable.HomogeneousObjectType;
import org.jenkinsci.plugins.structs.describable.ParameterType;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jsoup.Jsoup;
import org.jsoup.safety.Whitelist;
import org.kohsuke.stapler.NoStaplerConstructorException;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.stream.Collectors;

//fake out unit tests
import hudson.Main;

public class ToAsciiDoc {
    /**
     * Keeps track of nested {@link DescribableModel#getType()} to avoid recursion.
     */
    private static Stack<Class> nesting = new Stack<>();

    /** Asciidoc conversion functions. **/
    private static String header(int depth){
        return String.join("", Collections.nCopies(depth, "="));
    }

    private static String helpify(String help){
        return Jsoup.clean(help, Whitelist.relaxed().addEnforcedAttribute("a", "rel", "nofollow")) + "\n";
    }

    private static String describeType(ParameterType type) throws Exception {
        StringBuilder typeInfo = new StringBuilder();
        if (type instanceof AtomicType) {
            typeInfo.append("<li><b>Type:</b> <code>").append(type).append("</code></li>");
        } else if (type instanceof EnumType) {
            typeInfo
                  .append("<li><b>Values:</b> ")
                  .append(Arrays.stream((((EnumType) type).getValues())).map(v -> "<code>" + v + "</code>").collect(Collectors.joining(", ")))
                  .append("</li>");
        } else if (type instanceof ArrayType) {
            typeInfo
                  .append("<b>Array/List</b><br/>\n")
                  .append(describeType(((ArrayType) type).getElementType()));
        } else if (type instanceof HomogeneousObjectType) {
            typeInfo
                  .append("<b>Nested Object</b>\n")
                  .append(generateHelp(((HomogeneousObjectType) type).getSchemaType(), false));
        } else if (type instanceof HeterogeneousObjectType) {
            typeInfo
                  .append("<b>Nested Choice of Objects</b>\n");
            for (Map.Entry<String, DescribableModel<?>> entry : ((HeterogeneousObjectType) type).getTypes().entrySet()) {
                typeInfo
                      .append("<li><code>")
                      .append(DescribableModel.CLAZZ).append("</code>: <code>")
                      .append(entry.getKey()).append("</code></li>\n")
                      .append(generateHelp(entry.getValue(), true));
            }
        } else if (type instanceof ErrorType) { //Shouldn't hit this; open a ticket
            Exception x = ((ErrorType) type).getError();
            if(x instanceof NoStaplerConstructorException || x instanceof UnsupportedOperationException) {
                String msg = x.toString();
                typeInfo.append("<code>").append(msg.substring(msg.lastIndexOf(" ")).trim()).append("</code>\n");
            } else {
                typeInfo.append("<code>").append(x).append("</code>\n");
            }
        } else {
            assert false: type;
        }
        return typeInfo.toString();
    }

    private static String generateAttrHelp(DescribableParameter param) throws Exception {
        StringBuilder attrHelp = new StringBuilder();
        String help = param.getHelp();
        if (help != null && !help.equals("")) {
            attrHelp.append(helpify(help)).append("\n");
        }
        ParameterType type = param.getType();
        attrHelp.append("<ul>").append(describeType(type)).append("</ul>");
        return attrHelp.toString();
    }

    private static String generateHelp(DescribableModel<?> model, boolean indent) throws Exception {
        if (nesting.contains(model.getType()))
            return "";  // if we are recursing, cut the search
        nesting.push(model.getType());

        StringBuilder total = new StringBuilder();
        try {
            String help = model.getHelp();
            if (help != null && !help.equals("")) {
                total.append(helpify(help));
            }

            if (indent) {
                total.append("<ul>");
            }
            StringBuilder optionalParams = new StringBuilder();
            //for(DescribableParameter p : model.getParameters()){
            for(Object o : model.getParameters()) {
                DescribableParameter p = (DescribableParameter) o;
                if(p.isRequired()) {
                    total
                          .append("<li><code>").append(p.getName()).append("</code>").append("\n")
                          .append(generateAttrHelp(p))
                          .append("</li>\n");
                } else {
                    optionalParams
                          .append("<li><code>").append(p.getName()).append("</code> (optional)").append("\n")
                          .append(generateAttrHelp(p))
                          .append("</li>\n");
                }
            }
            total.append(optionalParams.toString());
            if (indent) {
                total.append("</ul>");
            }
        } finally {
            nesting.pop();
            return total.toString();
        }
    }

    /**
     * Generate documentation for a plugin step.
     */
    public static String generateStepHelp(StepDescriptor d){
        StringBuilder mkDesc = new StringBuilder(header(3)).append(" +").append(d.getFunctionName()).append("+: ").append(d.getDisplayName()).append("\n");
        mkDesc.append("++++\n");
        try{
            mkDesc.append(generateHelp(new DescribableModel(d.clazz), true))
                .append("\n\n");
        } catch(Exception ex){
            mkDesc.append("<code>").append(ex).append("</code>\n\n");
        } catch(Error err){
            mkDesc.append("<code>").append(err).append("</code>\n\n");
        }
        return mkDesc.append("\n++++\n").toString();
    }

    /**
     * Generate documentation for a {@link Descriptor}
     */
    private static String generateDescribableHelp(Descriptor d) {
        if (d instanceof StepDescriptor) {
            return generateStepHelp((StepDescriptor)d);
        } else {
            Set<String> symbols = SymbolLookup.getSymbolValue(d);
            if (!symbols.isEmpty()) {
                StringBuilder mkDesc = new StringBuilder(header(3)).append(" +").append(symbols.iterator().next()).append("+: ").append(d.getDisplayName()).append("\n");
                try {
                    mkDesc.append(generateHelp(new DescribableModel<>(d.clazz), true)).append("\n\n");
                } catch (Exception ex) {
                    mkDesc.append("+").append(ex).append("+\n\n");
                } catch (Error err) {
                    mkDesc.append("+").append(err).append("+\n\n");
                }
                return mkDesc.toString();
            } else {
                return null;
            }
        }
    }

    /**
     * Creates a header for use in JenkinsIO and other awestruct applications.
     */
    private static String generateHeader(String pluginName){
        StringBuilder head = new StringBuilder("---\nlayout: pipelinesteps\ntitle: \"");
        head.append(pluginName)
          .append("\"\n---\n")
		  .append("\n:notitle:\n:description:\n:author:\n:email: jenkinsci-users@googlegroups.com\n:sectanchors:\n:toc: left\n\n");

        return head.toString();
    }
    
    /**
     * Generate help documentation for an entire plugin.  Returns a String that can
     * be saved into a file.
     *
     * @return String  total documentation for the page
     */
    public static String generatePluginHelp(String pluginName, String displayName, Map<String, List<StepDescriptor>> byPlugin, boolean genHeader){
        Main.isUnitTest = true;

        //TODO: if condition
        StringBuilder whole9yards = new StringBuilder();
        if(genHeader){
            whole9yards.append(generateHeader(displayName));
        }
        
        whole9yards.append("== ").append(displayName).append("\n\n");
        whole9yards.append("plugin:").append(pluginName).append("[View this plugin on the Plugins Index]\n\n");
        for(String type : byPlugin.keySet()){
            for(StepDescriptor sd : byPlugin.get(type)){
                if (pluginName.equals("workflow-basic-steps") && sd.getFunctionName().equals("step")) {
                    /* this doesn't work right */
                    // TODO make this not super broken
                    continue;
                }
                whole9yards.append(generateStepHelp(sd));
            }
        }
        return whole9yards.toString();
    }

    public static String generateDirectiveHelp(String directiveName, Map<String, List<Descriptor>> descsByPlugin, boolean genHeader) {
        Main.isUnitTest = true;
        StringBuilder whole9yards = new StringBuilder();
        if(genHeader){
            whole9yards.append(generateHeader(directiveName));
        }
        whole9yards.append("== ").append(directiveName).append("\n\n");
        for (Map.Entry<String, List<Descriptor>> entry : descsByPlugin.entrySet()) {
            if (entry.getValue() != null && !entry.getValue().isEmpty()) {
                String pluginName = entry.getKey();
                if (pluginName.equals("core")) {
                    whole9yards.append("Jenkins Core:\n\n");
                } else {
                    whole9yards.append("plugin:").append(pluginName).append("[View this plugin on the Plugins Index]\n\n");
                }
                for (Descriptor d : entry.getValue()) {
                    whole9yards.append(generateDescribableHelp(d));
                }
            }
        }

        return whole9yards.toString();
    }
}