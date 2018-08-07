package org.logevents.formatting;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

public class ExceptionFormatter {

    private String[] packageFilter = new String[0];
    private boolean includePackagingData = false;

    protected static String newLine() {
        return System.getProperty("line.separator");
    }

    public String format(Throwable ex, Integer length) {
        if (ex == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        outputException(ex, null, length, "", "", builder);
        return builder.toString();
    }

    protected void outputException(Throwable ex, Throwable enclosing, Integer maxLength, String prefix, String indent, StringBuilder builder) {
        builder.append(indent).append(prefix).append(ex.toString()).append(newLine());

        outputStack(ex, maxLength, indent, enclosing, builder);

        for (Throwable suppressedException : ex.getSuppressed()) {
            outputException(suppressedException, ex, maxLength, "Suppressed: ", indent + "\t", builder);
        }

        Throwable cause = ex.getCause();
        if (cause != null) {
            outputException(cause, ex, maxLength, "Caused by: ", indent, builder);
        }
    }

    protected void outputStack(Throwable ex, Integer maxLength, String indent, Throwable enclosing, StringBuilder builder) {
        int uniquePrefix = uniquePrefix(ex, enclosing);
        StackTraceElement[] stackTrace = ex.getStackTrace();
        int ignored = 0;
        int actualLines = 0;
        for (int i = 0; i < uniquePrefix && actualLines < maxLength; i++) {
            if (isIgnored(stackTrace[i])) {
                ignored++;
            } else {
                outputStackFrame(stackTrace[i], indent, builder, ignored);
                actualLines++;
                ignored = 0;
            }
        }
        if (ignored > 0) {
            outputIgnoredLineCount(ignored, indent, builder).append(newLine());
        }
        if (uniquePrefix < stackTrace.length && uniquePrefix < maxLength) {
            builder.append(indent).append("\t... ").append(stackTrace.length - uniquePrefix).append(" more").append(newLine());
        }
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

    protected void outputStackFrame(StackTraceElement frame, String indent, StringBuilder builder, int ignored) {
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

    private String getPackagingData(StackTraceElement frame) {
        return getPackagingData(frame.getClassName());
    }

    private String getPackagingData(String className) {
        return "[" + getCodeSource(className) + ":" + getVersion(className) + "]";
    }

    private String getCodeSource(String className) {
        try {
            String classFile = String.join("/", className.split("\\.")) + ".class";
            URL resource = getClass().getResource("/" + classFile);
            if (resource == null) {
                return "na";
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

    private String getVersion(String className) {
        try {
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

}
