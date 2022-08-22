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

public class ProcessAsciiDoc {

    public void generateHeader(StringBuilder toWrite, String param) {
        toWrite.append("---\nlayout: pipelinesteps\ntitle: \"").append(param)
                .append("\"\n---\n")
                .append("== " + param + "\n")
                .append("\n++++\n");
    }

    /*
     * Takes in param name, and separates its documentation on to a new file.
     */
    public String separateParam(String param, String allAscii, File child)
            throws IOException, RuntimeException {
        BufferedReader br = new BufferedReader(new FileReader(child));
        String line;
        int counter = 0; // keeps a count of opening and closing li tags to mark the end of a section
        boolean flag = false;
        int lines = 0;
        String url = param.toLowerCase().replaceAll("class", "").replaceAll("[^a-zA-Z0-9]", "");
        Path adocPath = Path.of(allAscii + "/params/" + url + ".adoc"); // points to the params folder that will contain the separated files
        StringBuilder newAdoc = new StringBuilder(); // new file that contains only the parameter details
        StringBuilder duplicate = new StringBuilder(); // contains the content of the current file minus the parameter details
        generateHeader(newAdoc, param);

        while ((line = br.readLine()) != null) {
            if (!flag && line.contains("<li><code>" + param + "</code><div>")) {
                flag = true;
                duplicate.append("<li><span><a href=\"/doc/pipeline/steps/params/" + url
                        + "\" target=\"_blank\"><code>" + param + "</code></a></span></li>\n");
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
                            br.close();
                            throw new RuntimeException("Invalid Configuration, " + param
                                    + " does not have sufficient documentation to be separated!");
                            // halt the program if any of the configured parameters does not contain sufficient documentation
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
        br.close();
        return duplicate.toString();
    }

    public void processDocs(String allAscii) {
        File dir = new File(allAscii);
        File[] directoryListing = dir.listFiles();
        try {
            List<String> config = Files.readAllLines(Paths.get("config.txt"));
            for (String param : config) {
                if (directoryListing != null) {
                    for (File child : directoryListing) {
                        String type = FilenameUtils.getExtension(child.getName());
                        if (type.equals("adoc")) {
                            Path currentPath = Path.of(child.getPath());
                            Files.writeString(currentPath, separateParam(param, allAscii, child));
                        }
                    }
                }
            }
        } catch (IOException | RuntimeException ex) {
            ex.printStackTrace();
        }
    }
}