package org.logevents.formatting;

import org.logevents.status.LogEventStatus;
import org.logevents.util.Configuration;
import org.logevents.util.LogEventConfigurationException;
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
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.function.Function;

public class ExceptionFormatter {

    protected Map<String, Function<StackTraceElement, String>> sourcePackagePatterns = new HashMap<>();
    private String[] packageFilter = new String[0];
    private boolean includePackagingData = false;
    private int maxLength = Integer.MAX_VALUE;

    public ExceptionFormatter(Properties properties, String prefix) {
        Configuration configuration = new Configuration(properties, prefix);
        packageFilter = configuration.getStringList("packageFilter");
        includePackagingData = configuration.getBoolean("includePackagingData");
        maxLength = configuration.optionalInt("maxLength").orElse(Integer.MAX_VALUE);
        configuration.checkForUnknownFields();
    }

    public ExceptionFormatter() {
    }

    protected static String newLine() {
        return System.getProperty("line.separator");
    }

    public String format(Throwable ex) {
        if (ex == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        outputException(ex, null, "", "", builder);
        return builder.toString();
    }

    protected void outputException(Throwable ex, Throwable enclosing, String prefix, String indent, StringBuilder builder) {
        outputExceptionHeader(ex, prefix, indent, builder);

        outputStack(ex, indent, enclosing, builder);

        for (Throwable suppressedException : ex.getSuppressed()) {
            outputException(suppressedException, ex, "Suppressed: ", indent + "\t", builder);
        }

        Throwable cause = ex.getCause();
        if (cause != null) {
            outputException(cause, ex, "Caused by: ", indent, builder);
        }
    }

    protected void outputExceptionHeader(Throwable ex, String prefix, String indent, StringBuilder builder) {
        builder.append(indent).append(prefix).append(ex.toString()).append(newLine());
    }

    protected void outputStack(Throwable ex, String indent, Throwable enclosing, StringBuilder builder) {
        int uniquePrefix = uniquePrefix(ex, enclosing);
        StackTraceElement[] stackTrace = ex.getStackTrace();
        int ignored = 0;
        int actualLines = 0;
        for (int i = 0; i < uniquePrefix && actualLines < maxLength; i++) {
            if (isIgnored(stackTrace[i])) {
                ignored++;
            } else {
                outputStackFrame(stackTrace[i], ignored, indent, builder);
                actualLines++;
                ignored = 0;
            }
        }
        if (ignored > 0) {
            outputIgnoredLineCount(ignored, indent, builder).append(newLine());
        }
        if (uniquePrefix < stackTrace.length && uniquePrefix < maxLength) {
            outputIgnoredCommonLines(stackTrace.length - uniquePrefix, indent, builder);
        }
    }

    protected void outputIgnoredCommonLines(int commonLines, String indent, StringBuilder builder) {
        builder.append(indent).append("\t... ").append(commonLines).append(" more").append(newLine());
    }

    protected StringBuilder outputIgnoredLineCount(int ignored, String indent, StringBuilder builder) {
        return builder.append(indent).append("[").append(ignored).append(" skipped]");
    }

    protected boolean isIgnored(StackTraceElement frame) {
        for (String filter : this.packageFilter) {
            if (frame.getClassName().startsWith(filter)) {
                return true;
            }
        }
        return false;
    }

    protected void outputStackFrame(StackTraceElement frame, int ignored, String indent, StringBuilder builder) {
        builder.append(indent).append("\tat ").append(frame);
        if (ignored > 0) {
            builder.append(" ");
            outputIgnoredLineCount(ignored, indent, builder);
        }
        if (includePackagingData) {
            builder.append(" ").append(getPackagingData(frame));
        }
        builder.append(newLine());
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

    public void setPackageFilter(String[] packageFilter) {
        this.packageFilter = packageFilter;
    }

    public void setIncludePackagingData(boolean includePackagingData) {
        this.includePackagingData = includePackagingData;
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

    public void addPackageMavenLocation(String sourcePackages, String mavenLocation) {
        try (InputStream pomResource = getClass().getResourceAsStream("/META-INF/maven/" + mavenLocation + "/pom.xml")) {
            if (pomResource == null) {
                LogEventStatus.getInstance().addInfo(this, mavenLocation + " pom.xml not found");
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

    public void configureSourceCode(Configuration configuration) {
        configuration.optionalString("sourceCode");

        int index = 1;
        Optional<String> sourcePackages;
        while ((sourcePackages = configuration.optionalString("sourceCode." + index + ".package")).isPresent()) {
            Optional<String> githubLocation = configuration.optionalString("sourceCode." + index + ".github");
            Optional<String> mavenLocation = configuration.optionalString("sourceCode." + index + ".maven");
            Optional<String> bitbucketLocation = configuration.optionalString("sourceCode." + index + ".bitbucket");

            if (githubLocation.isPresent()) {
                addPackageGithubLocation(sourcePackages.get(), githubLocation.get(), configuration.optionalString("sourceCode." + index + ".tag"));
            } else if (bitbucketLocation.isPresent()) {
                addPackageBitbucket5Location(sourcePackages.get(), bitbucketLocation.get(), configuration.optionalString("sourceCode." + index + ".tag"));
            } else if (mavenLocation.isPresent()) {
                addPackageMavenLocation(sourcePackages.get(), mavenLocation.get());
            } else {
                throw new LogEventConfigurationException("Can't find source code location for " + sourcePackages);
            }
            index++;
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
