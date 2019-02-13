package org.logevents.observers.batch;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.function.Function;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.logevents.formatting.CauseFirstExceptionFormatter;
import org.logevents.status.LogEventStatus;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

public class SlackExceptionFormatter extends CauseFirstExceptionFormatter {

    private Map<String, Function<StackTraceElement, String>> sourcePackagePatterns = new HashMap<>();

    public SlackExceptionFormatter() {
        super(new Properties(), "");
    }

    @Override
    protected void outputStackFrame(StackTraceElement frame, int ignored, String indent, StringBuilder builder) {
        String sourceLink = getSourceLink(frame);
        if (sourceLink == null) {
            builder.append(indent).append("\tat ").append(frame);
        } else {
            builder.append(indent).append("\tat ").append("<").append(sourceLink)
            .append("|").append(frame.getClassName()).append(".").append(frame.getMethodName())
            .append(" 🔗>");
        }
        if (ignored > 0) {
            builder.append(" ");
            outputIgnoredLineCount(ignored, indent, builder);
        }
        builder.append(newLine());
    }

    protected String getSourceLink(StackTraceElement stackTraceElement) {
        for (String packagePrefix : sourcePackagePatterns.keySet()) {
            if (stackTraceElement.getClassName().startsWith(packagePrefix)) {
                return sourcePackagePatterns.get(packagePrefix).apply(stackTraceElement);
            }
        }

        return null;
    }

    void addPackageUrlPattern(String sourcePackages, Function<StackTraceElement, String> transform) {
        for (String packagePrefix : sourcePackages.split(",")) {
            sourcePackagePatterns.put(packagePrefix.trim(), transform);
        }
    }

    private String fileName(StackTraceElement stackTraceElement) {
        return stackTraceElement.getClassName().replaceAll("\\.", "/") + ".java";
    }

    public void addPackageMavenLocation(String sourcePackages, String mavenLocation) {
        try (InputStream pomResource = getClass().getResourceAsStream("/META-INF/maven/" + mavenLocation + "/pom.xml")) {
            if (pomResource == null) {
                LogEventStatus.getInstance().addInfo(this, mavenLocation + " pom.xml not found");
                return;
            }

            Document pom = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(pomResource);
            NodeList scmElements = pom.getDocumentElement().getElementsByTagName("scm");
            if (scmElements.getLength() == 1) {
                NodeList urlElements = ((Element) scmElements.item(0)).getElementsByTagName("url");
                NodeList tagsElements = ((Element) scmElements.item(0)).getElementsByTagName("tag");

                if (urlElements.getLength() == 1) {
                    String url = ((Element) urlElements.item(0)).getTextContent().trim();

                    String tag = "master";
                    if (tagsElements.getLength() == 1) {
                        tag = tagsElements.item(0).getTextContent().trim();
                        if (tag.equals("HEAD")) tag = "master";
                    }

                    if (url.startsWith("https://github.com/")) {
                        addPackageGithubLocation(sourcePackages, url, Optional.of(tag));
                    }
                }
            }
        } catch (SAXException | ParserConfigurationException | IOException e) {
            LogEventStatus.getInstance().addError(this, "Failed to read " + mavenLocation + "/pom.xml", e);
        }
    }

    public void addPackageGithubLocation(String sourcePackages, String project, Optional<String> tag) {
        String url = project.startsWith("https://") ? project : "https://github.com/" + project;
        if (url.endsWith(".git")) {
            url = url.substring(0, url.length() - ".git".length());
        }
        if (!url.endsWith("/")) {
            url += "/";
        }
        String pattern = url + "blob/" + tag.orElse("master") + "/src/main/java/%s#L%s";
        addPackageUrlPattern(sourcePackages,
                ste -> String.format(pattern, fileName(ste), ste.getLineNumber()));
    }

    public void addPackageBitbucket5Location(String sourcePackages, String url, Optional<String> tag) {
        addPackageUrlPattern(sourcePackages,
                ste -> url + "/src/main/java/" + fileName(ste) + "?at=" + tag.orElse("master") + "#" + ste.getLineNumber());
    }
}
