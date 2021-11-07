package org.logevents.formatting;

import org.logevents.config.Configuration;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Presents the exception of a Log Event. Supports filtering stack traces by package and
 * (for {@link org.logevents.observers.batch.SlackExceptionFormatter})
 * including a link to the corresponding {@link SourceCodeLookup}.
 * <p>
 * Example configuration
 *
 * <pre>
 * observer.x.formatter.exceptionFormatter={@link CauseFirstExceptionFormatter}
 * observer.x.formatter.exceptionFormatter.packageFilter=sun.www, com.example.uninteresting
 * observer.x.formatter.sourceCode.1.package=org.logevents
 * observer.x.formatter.sourceCode.1.github=jhannes/logevents
 * </pre>
 *
 * You can also specify package filters for all observers:
 * <pre>
 * observer.*.packageFilter=sun.www, com.example.uninteresting
 * </pre>
 *
 * @author Johannes Brodwall
 */
public abstract class AbstractExceptionFormatter {

    protected SourceCodeLookup sourceCodeLookup;
    protected boolean includePackagingData;
    protected List<String> packageFilter = new ArrayList<>();
    protected int maxLength = Integer.MAX_VALUE;

    public AbstractExceptionFormatter() {
        includePackagingData = false;
        sourceCodeLookup = new SourceCodeLookup();
    }

    public AbstractExceptionFormatter(Map<String, String> properties, String prefix) {
        this(new Configuration(properties, prefix));
    }

    public AbstractExceptionFormatter(Configuration configuration) {
        packageFilter = configuration.getPackageFilter();
        includePackagingData = configuration.getBoolean("includePackagingData");
        maxLength = configuration.optionalInt("maxLength").orElse(Integer.MAX_VALUE);
        configureSourceCode(configuration);
        configuration.checkForUnknownFields();
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

    public void configureSourceCode(Configuration configuration) {
        setSourceCodeLookup(createSourceCodeLookup(configuration));
    }

    private SourceCodeLookup createSourceCodeLookup(Configuration configuration) {
        return configuration.createInstanceOrGlobal("sourceCode", SourceCodeLookup.class, SourceCodeLookup.class);
    }

    public void setSourceCodeLookup(SourceCodeLookup sourceCodeLookup) {
        this.sourceCodeLookup = sourceCodeLookup;
    }
}
