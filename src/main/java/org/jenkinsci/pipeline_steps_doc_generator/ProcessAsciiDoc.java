package org.jenkinsci.pipeline_steps_doc_generator;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.apache.commons.io.FilenameUtils;

/**
 * Acts as a processing layer after the AsciiDocs are generated from
 * {@link ToAsciiDoc}.
 * It removes redundancy of the configured parameter class names, by creating a
 * global document of the class that all Pipeline steps can refer to.
 */
public class ProcessAsciiDoc {

    public void generateHeader(StringBuilder toWrite, String className) {
        toWrite.append("---\nlayout: pipelinesteps\ntitle: \"").append(className)
                .append("\"\n---\n")
                .append("== " + className + "\n")
                .append("\n++++\n");
    }

    /**
     * Takes in a Pipeline step parameter's class name, and separates its
     * documentation on to a new file.
     */

    public String separateClass(String className, String allAscii, File child)
            throws IOException, RuntimeException {
        BufferedReader br = new BufferedReader(new FileReader(child));
        StringBuilder duplicate = new StringBuilder(); // contains the content of the current file minus the parameter
                                                       // details
        String line;
        int counter = 0; // keeps a count of opening and closing li tags to mark the end of a section
        boolean flag = false;
        int lines = 0;
        String url = className.toLowerCase().replaceAll("class", "").replaceAll("[^a-zA-Z0-9]", "");
        Path adocPath = Path.of(allAscii + "/params/" + url + ".adoc"); // points to the params folder that will contain
                                                                        // the separated files
        StringBuilder newAdoc = new StringBuilder(); // new file that contains only the parameter details
        generateHeader(newAdoc, className);
        try {
            while ((line = br.readLine()) != null) {
                if (!flag && line.contains("<li><code>" + className + "</code><div>")) {
                    flag = true;
                    duplicate.append("<li><span><a href=\"/doc/pipeline/steps/params/" + url
                            + "\" target=\"_blank\"><code>" + className + "</code></a></span></li>\n");
                } else if (!flag) {
                    duplicate.append(line).append("\n");
                }
                if (flag) {
                    counter += (line.split("<li>", -1).length - line.split("</li>", -1).length);
                    lines++;
                    if (newAdoc != null)
                        newAdoc.append(line).append("\n");
                    if (counter == 0) {
                        if (newAdoc != null) {
                            if (lines < 500) {
                                throw new RuntimeException("Invalid Configuration, " + className
                                        + " does not have sufficient documentation to be separated!");
                                // halt the program if any of the configured parameters does not contain
                                // sufficient documentation
                            }
                            newAdoc.append("\n\n++++");
                            Files.writeString(adocPath, newAdoc.toString());
                            newAdoc = null;
                        }
                        lines = 0;
                        flag = false;
                    }
                }
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        } finally {
            br.close();
        }
        return duplicate.toString();
    }

    /**
     * @param allAscii path to the directory containing all the AsciiDocs.
     * @throws RuntimeException thrown if a configured class lacks sufficient documentation.
     * @throws IOException
     */
    public void processDocs(String allAscii) {
        File dir = new File(allAscii);
        File[] directoryListing = dir.listFiles();
        try {
            List<String> config = Files.readAllLines(Paths.get("config.txt"));
            for (String className : config) {
                if (directoryListing != null) {
                    for (File child : directoryListing) {
                        String type = FilenameUtils.getExtension(child.getName());
                        if (type.equals("adoc")) {
                            Path currentPath = Path.of(child.getPath());
                            Files.writeString(currentPath, separateClass(className, allAscii, child));
                        }
                    }
                }
            }
        } catch (IOException | RuntimeException ex) {
            ex.printStackTrace();
        }
    }
}