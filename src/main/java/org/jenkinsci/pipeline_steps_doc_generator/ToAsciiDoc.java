package org.jenkinsci.pipeline_steps_doc_generator;

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
import org.jvnet.hudson.reactor.Reactor;
import org.jvnet.hudson.reactor.ReactorException;
import org.jvnet.hudson.reactor.Task;
import org.jvnet.hudson.reactor.TaskBuilder;
import org.kohsuke.stapler.NoStaplerConstructorException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Stack;

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

    private static String listDepth(int depth){
        return String.join("", Collections.nCopies(depth+1, ":"));
    }

    /**
     * Convert some special tags to their asciidoc equivalents.
     */
    private static String stripDiv(String tagged){
        //strip whitespace between tags
        Matcher m = Pattern.compile("<p>((.|\n|\r)+?)</p>").matcher(tagged);
        StringBuffer bufStr = new StringBuffer();
        while(m.find()){
            String rep = m.group();
            rep = rep.replaceAll("\\n\\s+", "\n");
	        m.appendReplacement(bufStr, Matcher.quoteReplacement(rep));
        }
        m.appendTail(bufStr);
        tagged = bufStr.toString();

        tagged = tagged.replaceAll("</?pre></?code>", "\n----\n")
          .replaceAll("/?code></?pre>", "\n----\n") //otherwise hanging ``
          .replaceAll("</?code>", "`")
          .replaceAll("</?pre>", "\n----\n")
          .replaceAll("</?div>", "")
          .replaceAll("</?p>", "\n\n");
        
        return tagged.replaceAll("<(.|\n)*?>", "").trim();
    }

    private static String helpify(String help){
        return "====\n"+stripDiv(help)+"\n====\n";
    }

    private static String describeType(ParameterType type, int headerLevel) throws Exception {
        StringBuilder typeInfo = new StringBuilder();
        int nextHeaderLevel = Math.min(6, headerLevel + 1);
        if (type instanceof AtomicType) {
            typeInfo.append("*Type:* ").append(type).append("\n");
        } else if (type instanceof EnumType) {
            typeInfo.append("*Values:*\n\n");
            for (String v : ((EnumType) type).getValues()) {
                typeInfo.append("* +").append(v).append("+\n");
            }
        } else if (type instanceof ArrayType) {
            typeInfo.append("*Array/List*\n\n");
            typeInfo.append(describeType(((ArrayType) type).getElementType(), headerLevel));
        } else if (type instanceof HomogeneousObjectType) {
            typeInfo.append("Nested Object\n\n");
            typeInfo.append(generateHelp(((HomogeneousObjectType) type).getSchemaType(), nextHeaderLevel));
        } else if (type instanceof HeterogeneousObjectType) {
            typeInfo.append("Nested Choice of Objects\n");
            for (Map.Entry<String, DescribableModel<?>> entry : ((HeterogeneousObjectType) type).getTypes().entrySet()) {
                typeInfo.append("+").append(DescribableModel.CLAZZ).append(": '").append(entry.getKey()).append("'+\n");  //FIX
                typeInfo.append(generateHelp(entry.getValue(), nextHeaderLevel));
            }
        } else if (type instanceof ErrorType) { //Shouldn't hit this; open a ticket
            Exception x = ((ErrorType) type).getError();
            if(x instanceof NoStaplerConstructorException) {
                String msg = x.toString();
                typeInfo.append("+").append(msg.substring(msg.lastIndexOf(" "))).append("+\n");
            } else {
                typeInfo.append("+").append(x).append("+\n");
            }
        } else {
            assert false: type;
        }
        return typeInfo.toString();
    }

    private static String generateAttrHelp(DescribableParameter param, int headerLevel) throws Exception {
        StringBuilder attrHelp = new StringBuilder();
        String help = param.getHelp();
        if (help != null && !help.equals("")) {
            attrHelp.append(stripDiv(help)).append("\n");
        }
        ParameterType type = param.getType();
        attrHelp.append(describeType(type, headerLevel));
        return attrHelp.toString();
    }

    private static String generateHelp(DescribableModel<?> model, int headerLevel) throws Exception {
        if (nesting.contains(model.getType()))
            return "";  // if we are recursing, cut the search
        nesting.push(model.getType());

        try {
            StringBuilder total = new StringBuilder();
            String help = model.getHelp();
            if (help != null && !help.equals("")) {
                total.append(helpify(help));
            }

            StringBuilder optionalParams = new StringBuilder();
            //for(DescribableParameter p : model.getParameters()){
            for(Object o : model.getParameters()){
                DescribableParameter p = (DescribableParameter) o;
                if(p.isRequired()){
                    total.append("+").append(p.getName()).append("+").append(listDepth(headerLevel)).append("\n+\n");
                    total.append(generateAttrHelp(p, headerLevel));
                    total.append("\n\n");
                } else {
                    optionalParams.append("+").append(p.getName()).append("+ (optional)").append(listDepth(headerLevel)).append("\n+\n");
                    optionalParams.append(generateAttrHelp(p, headerLevel));
                    optionalParams.append("\n\n");
                }
            }
            total.append(optionalParams.toString());

            return total.toString();
        } finally {
            nesting.pop();
        }
    }

    /**
     * Generate documentation for a plugin step.
     */
    public static String generateStepHelp(StepDescriptor d){
        StringBuilder mkDesc = new StringBuilder(header(3)).append(" +").append(d.getFunctionName()).append("+: ").append(d.getDisplayName()).append("\n");
        try{
            mkDesc.append(generateHelp(new DescribableModel(d.clazz), 1))
                .append("\n\n");
        } catch(Exception ex){
            mkDesc.append("+").append(ex).append("+\n\n");
        } catch(Error err){
            mkDesc.append("+").append(err).append("+\n\n");
        }
        return mkDesc.toString();
    }
    
    /**
     * Creates a header for use in JenkinsIO and other awestruct applications.
     */
    private static String generateHeader(String pluginName){
        StringBuilder head = new StringBuilder("---\nlayout: simplepage\ntitle: \"");
        head.append(pluginName)
          .append("\"\n---\n:doctitle: ")
          .append(pluginName)
		  .append("\n:notitle:\n:description:\n:author:\n:email: jenkinsci-users@googlegroups.com\n:sectanchors:\n:toc: left\n\n");

        return head.toString();
    }
    
    /**
     * Generate help documentation for an entire plugin.  Returns a String that can
     * be saved into a file.
     *
     * @return String  total documentation for the page
     */
    public static String generatePluginHelp(String pluginName, Map<String, List<StepDescriptor>> byPlugin, boolean genHeader){
        Main.isUnitTest = true;

        //TODO: if condition
        StringBuilder whole9yards = new StringBuilder();
        if(genHeader){
            whole9yards.append(generateHeader(pluginName));
        }
        
        whole9yards.append("== ").append(pluginName).append("\n\n");
        for(String type : byPlugin.keySet()){
            for(StepDescriptor sd : byPlugin.get(type)){
                whole9yards.append(generateStepHelp(sd));
            }
        }
        return whole9yards.toString();
    }
}