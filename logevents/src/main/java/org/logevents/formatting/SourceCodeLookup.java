package org.logevents.formatting;

import org.logevents.config.Configuration;
import org.logevents.config.LogEventConfigurationException;
import org.logevents.status.LogEventStatus;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.function.Function;

/**
 * Configure links to source code (for ExceptionFormatters that support it),
 * based on package names. Currently, Log Events understand how to read
 * (some) &lt;scm&gt; settings from <code>.pom</code>-files
 * {@link #addPackageMavenLocation(String, String)}
 * and how to set up a link to a simple Github ({@link #addPackageGithubLocation} or
 * Bitbucket ({@link #addPackageBitbucket5Location}) project. Example configuration:
 *
 * <pre>
 * observer.*.sourceCode.1.packages=org.logevents
 * # See if META-INF/maven/org.logevents/logevents/pom.xml is available
 * #  If so, look for the &lt;scm&gt; tag in the pom-file
 * observer.*.sourceCode.1.maven=org.logevents/logevents
 *
 * observer.*.sourceCode.2.packages=org.slf4j
 * # Link to Github
 * observer.*.sourceCode.2.github=https://github.com/qos-ch/slf4j
 * observer.*.sourceCode.2.tag=v_1.7.25
 *
 * observer.*.sourceCode.3.packages=com.myproject
 * # Link to Bitbucket: https://bitbucket.example.com/EX/project/src/main/java/{file-path}?at=release#{line}
 * observer.*.sourceCode.3.github=https://bitbucket.example.com/EX/project/
 * observer.*.sourceCode.3.tag=release
 * </pre>
 *
 * You can customize source code lookup by subclassing:
 *
 * <pre>
 * observer.*.sourceCode=com.example.logging.SourceCodeLookup
 * </pre>
 */
public class SourceCodeLookup {
    protected final Map<String, Function<StackTraceElement, String>> sourcePackagePatterns = new LinkedHashMap<>();

    public SourceCodeLookup(Properties properties, String prefix) {
        this(new Configuration(properties, prefix));
    }

    public SourceCodeLookup(Configuration configuration) {
        configureSourceCode(configuration);
    }

    public SourceCodeLookup() {
    }

    public String getSourceLink(StackTraceElement stackTraceElement) {
        for (String packagePrefix : sourcePackagePatterns.keySet()) {
            if (stackTraceElement.getClassName().startsWith(packagePrefix)) {
                return sourcePackagePatterns.get(packagePrefix).apply(stackTraceElement);
            }
        }
        return null;
    }

    private void configureSourceCode(Configuration configuration) {
        int index = 1;
        Optional<String> sourcePackages;
        while ((sourcePackages = configuration.optionalString(index + ".package")).isPresent()) {
            Optional<String> githubLocation = configuration.optionalString(index + ".github");
            Optional<String> mavenLocation = configuration.optionalString(index + ".maven");
            Optional<String> bitbucketLocation = configuration.optionalString(index + ".bitbucket");
            Optional<String> tag = configuration.optionalString(index + ".tag");
            addPackageLocation(sourcePackages.get(), githubLocation, mavenLocation, bitbucketLocation, tag);
            index++;
        }

        index = 1;
        while ((sourcePackages = configuration.optionalDefaultString("sourceCode." + index + ".package")).isPresent()) {
            Optional<String> githubLocation = configuration.optionalDefaultString("sourceCode." + index + ".github");
            Optional<String> mavenLocation = configuration.optionalDefaultString("sourceCode." + index + ".maven");
            Optional<String> bitbucketLocation = configuration.optionalDefaultString("sourceCode." + index + ".bitbucket");
            Optional<String> tag = configuration.optionalDefaultString("sourceCode." + index + ".tag");
            addPackageLocation(sourcePackages.get(), githubLocation, mavenLocation, bitbucketLocation, tag);
            index++;
        }
    }

    public void addPackageLocation(String sourcePackages, Optional<String> githubLocation, Optional<String> mavenLocation, Optional<String> bitbucketLocation, Optional<String> tag) {
        if (githubLocation.isPresent()) {
            addPackageGithubLocation(sourcePackages, githubLocation.get(), tag);
        } else if (bitbucketLocation.isPresent()) {
            addPackageBitbucket5Location(sourcePackages, bitbucketLocation.get(), tag);
        } else if (mavenLocation.isPresent()) {
            addPackageMavenLocation(sourcePackages, mavenLocation.get());
        } else {
            throw new LogEventConfigurationException("Can't find source code location for " + sourcePackages);
        }
    }

    /**
     * Looks for POM file in <code>/META-INF/maven/{mavenLocation}/pom.xml</code> and
     * parses <code>&lt;scm&gt;&lt;url /&gt;&lt;tag /&gt;&lt;/scm&gt;</code> to find
     * source code location and tag. <strong>Currently only supports Github</strong>.
     */
    public void addPackageMavenLocation(String sourcePackages, String mavenLocation) {
        try (InputStream pomResource = getClass().getResourceAsStream("/META-INF/maven/" + mavenLocation + "/pom.xml")) {
            if (pomResource == null) {
                LogEventStatus.getInstance().addError(this, mavenLocation + " pom.xml not found", null);
                return;
            }

            addPackageMavenLocation(sourcePackages, pomResource);
        } catch (SAXException | ParserConfigurationException | IOException e) {
            LogEventStatus.getInstance().addError(this, "Failed to read " + mavenLocation + "/pom.xml", e);
        }
    }

    public void addPackageMavenLocation(String sourcePackages, InputStream pomResource) throws SAXException, IOException, ParserConfigurationException {
        Document pom = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(pomResource);
        NodeList scmElements = pom.getDocumentElement().getElementsByTagName("scm");
        if (scmElements.getLength() == 1) {
            NodeList urlElements = ((Element) scmElements.item(0)).getElementsByTagName("url");
            NodeList tagsElements = ((Element) scmElements.item(0)).getElementsByTagName("tag");

            if (urlElements.getLength() == 1) {
                String url = urlElements.item(0).getTextContent().trim();

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
    }

    /**
     * Links to <code>https://github.com/{project}/blob/{tag}{/module}/src/main/java/{filename}#L{line}</code>
     */
    public void addPackageGithubLocation(String sourcePackages, String project, Optional<String> tag) {
        String url = project.startsWith("https://") ? project : "https://github.com/" + project;
        if (url.endsWith(".git")) {
            url = url.substring(0, url.length() - ".git".length());
        }
        if (!url.endsWith("/")) {
            url += "/";
        }
        String module = "";
        int pathStarts = url.indexOf('/', "https://".length())+1;
        String[] pathSegments = url.substring(pathStarts).split("/");
        if (pathSegments.length > 2) {
            url = url.substring(0, pathStarts) + pathSegments[0] + "/" + pathSegments[1] + "/";
            module = "/" + pathSegments[2];
        }

        String pattern = url + "blob/" + tag.orElse("master") + module + "/src/main/java/%s#L%s";
        addPackageUrlPattern(sourcePackages,
                ste -> String.format(pattern, fileName(ste), ste.getLineNumber()));
    }

    /**
     * Links to <code>{url}/src/main/java/{filename}?at={tag}#{line}</code>
     */
    public void addPackageBitbucket5Location(String sourcePackages, String url, Optional<String> tag) {
        addPackageUrlPattern(sourcePackages,
                ste -> url + "/src/main/java/" + fileName(ste) + "?at=" + tag.orElse("master") + "#" + ste.getLineNumber());
    }

    public void addPackageUrlPattern(String sourcePackages, Function<StackTraceElement, String> transform) {
        for (String packagePrefix : sourcePackages.split(",")) {
            sourcePackagePatterns.put(packagePrefix.trim(), transform);
        }
    }

    private String fileName(StackTraceElement stackTraceElement) {
        return stackTraceElement.getClassName().replaceAll("\\.", "/") + ".java";
    }
}
