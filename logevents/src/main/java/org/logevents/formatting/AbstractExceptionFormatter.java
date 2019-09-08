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
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.function.Function;

public abstract class AbstractExceptionFormatter {

    protected final Map<String, Function<StackTraceElement, String>> sourcePackagePatterns = new LinkedHashMap<>();
    protected final boolean includePackagingData;
    private List<String> packageFilter = new ArrayList<>();
    protected int maxLength = Integer.MAX_VALUE;

    public AbstractExceptionFormatter(Properties properties, String prefix) {
        Configuration configuration = new Configuration(properties, prefix);
        packageFilter = configuration.getStringList("packageFilter");
        if (packageFilter.isEmpty()) {
            packageFilter = configuration.getDefaultStringList("packageFilter");
        }
        includePackagingData = configuration.getBoolean("includePackagingData");
        maxLength = configuration.optionalInt("maxLength").orElse(Integer.MAX_VALUE);
        configureSourceCode(configuration);
        configuration.checkForUnknownFields();
    }

    public AbstractExceptionFormatter() {
        includePackagingData = false;
    }

    protected static String newLine() {
        return System.getProperty("line.separator");
    }

    protected boolean isIgnored(StackTraceElement frame) {
        for (String filter : this.packageFilter) {
            if (frame.getClassName().startsWith(filter)) {
                return true;
            }
        }
        return false;
    }

    protected String getPackagingData(StackTraceElement frame) {
        return getPackagingData(frame.getClassName());
    }

    protected String getPackagingData(String className) {
        return "[" + getCodeSource(className) + ":" + getVersion(className) + "]";
    }

    protected String getCodeSource(String className) {
        try {
            String classFile = String.join("/", className.split("\\.")) + ".class";
            URL resource = getClass().getResource("/" + classFile);
            if (resource == null) {
                return "na";
            } else if (resource.getProtocol().equals("jrt")) {
                return "rt.jar";
            } else if (!resource.getProtocol().equals("jar")) {
                Path classFileFullPath = Paths.get(resource.toURI());
                Path classFileRelativePath = Paths.get(classFile);

                return Paths.get(classFileFullPath.toString().substring(0, classFileFullPath.toString().length() - classFileRelativePath.toString().length()))
                        .getFileName().toString();
            } else {
                String jarFile = resource.getFile().split("!")[0];
                return Paths.get(new URL(jarFile).toURI()).getFileName().toString();
            }
        } catch (URISyntaxException|IOException e) {
            return "na";
        }
    }

    protected String getVersion(String className) {
        try {
            String classFile = String.join("/", className.split("\\.")) + ".class";
            URL resource = getClass().getResource("/" + classFile);

            if (resource != null && resource.getProtocol().equals("jrt")) {
                return System.getProperty("java.version");
            }

            return Optional.ofNullable(Class.forName(className).getPackage().getImplementationVersion())
                    .orElse("na");
        } catch (ClassNotFoundException e) {
            return "na";
        }
    }

    protected int uniquePrefix(Throwable ex, Throwable enclosing) {
        int uniquePrefix = ex.getStackTrace().length;
        if (enclosing != null) {
            int commonStackStart = findCommonStart(enclosing.getStackTrace(), ex.getStackTrace());
            uniquePrefix = ex.getStackTrace().length - commonStackStart;
        }
        return uniquePrefix;
    }

    protected int findCommonStart(StackTraceElement[] enclosingTrace, StackTraceElement[] trace) {
        int i = 0;
        while (i < enclosingTrace.length && i < trace.length) {
            if (!trace[trace.length-1-i].equals(enclosingTrace[enclosingTrace.length - 1 - i])) {
                return i;
            }
            i++;
        }
        return i;
    }

    public void setPackageFilter(List<String> packageFilter) {
        this.packageFilter = packageFilter;
    }

    public void setMaxLength(int maxLength) {
        this.maxLength = maxLength;
    }

    void addPackageUrlPattern(String sourcePackages, Function<StackTraceElement, String> transform) {
        for (String packagePrefix : sourcePackages.split(",")) {
            sourcePackagePatterns.put(packagePrefix.trim(), transform);
        }
    }

    private String fileName(StackTraceElement stackTraceElement) {
        return stackTraceElement.getClassName().replaceAll("\\.", "/") + ".java";
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

    /**
     * Configure links to source code (for ExceptionFormatters that support it),
     * based on package names. Currently, Log Events understand how to read
     * (some) &lt;scm&gt; settings from <code>.pom</code>-files
     * {@link #addPackageMavenLocation(String, String)}
     * and how to set up a link to a simple Github ({@link #addPackageGithubLocation} or
     * Bitbucket ({@link #addPackageBitbucket5Location}) project. Example configuration:
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
     */
    public void configureSourceCode(Configuration configuration) {
        configuration.optionalString("sourceCode");

        int index = 1;
        Optional<String> sourcePackages;
        while ((sourcePackages = configuration.optionalString("sourceCode." + index + ".package")).isPresent()) {
            Optional<String> githubLocation = configuration.optionalString("sourceCode." + index + ".github");
            Optional<String> mavenLocation = configuration.optionalString("sourceCode." + index + ".maven");
            Optional<String> bitbucketLocation = configuration.optionalString("sourceCode." + index + ".bitbucket");
            Optional<String> tag = configuration.optionalString("sourceCode." + index + ".tag");
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

    protected String getSourceLink(StackTraceElement stackTraceElement) {
        for (String packagePrefix : sourcePackagePatterns.keySet()) {
            if (stackTraceElement.getClassName().startsWith(packagePrefix)) {
                return sourcePackagePatterns.get(packagePrefix).apply(stackTraceElement);
            }
        }

        return null;
    }

}
