package org.logevents.extend.servlets;

import org.junit.Test;
import org.logevents.config.Configuration;
import org.logevents.formatting.ExceptionFormatter;
import org.logevents.util.JsonUtil;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class JsonExceptionFormatterTest {

    private final StackTraceElement mainMethod = new StackTraceElement("org.logeventsdemo.MyApplication", "main", "MyApplication.java", 20);
    private final StackTraceElement publicMethod = new StackTraceElement("org.logeventsdemo.internal.MyClassName", "publicMethod", "MyClassName.java", 31);
    private final StackTraceElement internalMethod = new StackTraceElement("org.logeventsdemo.internal.MyClassName", "internalMethod", "MyClassName.java", 311);
    private final StackTraceElement nioApiMethod = new StackTraceElement("java.nio.file.Files", "write", "Files.java", 3292);
    private final StackTraceElement nioInternalMethod = new StackTraceElement("sun.nio.fs.WindowsException", "translateToIOException", "WindowsException.java", 79);
    private final StackTraceElement ioApiMethod = new StackTraceElement("java.io.FilterOutputStream", "close", "FilterOutputStream.java", 180);
    private final StackTraceElement ioInternalMethod = new StackTraceElement("java.io.FileOutputStream", "close", "FileOutputStream.java", 323);
    private final JsonExceptionFormatter formatter = new JsonExceptionFormatter(new HashMap<>(), "formatter");
    private final Map<String, String> properties = new HashMap<>();

    @Test
    public void shouldFormatSimpleException() {
        IOException simpleException = new IOException("This went wrong");
        simpleException.setStackTrace(new StackTraceElement[] { publicMethod, mainMethod });

        List<Map<String, Object>> stackTrace = formatter.createStackTrace(simpleException);
        Map<String, Object> jsonStackFrame = JsonUtil.getObject(stackTrace, 0);

        assertEquals(publicMethod.getClassName(), jsonStackFrame.get("className"));
        assertEquals(publicMethod.getMethodName(), jsonStackFrame.get("methodName"));
        assertEquals(publicMethod.getLineNumber() + "", jsonStackFrame.get("lineNumber"));
        assertEquals(publicMethod.getFileName(), jsonStackFrame.get("fileName"));

    }

    @Test
    public void shouldNotThrowException() {
        assertEquals(new ArrayList<>(), formatter.createStackTrace(null));
    }

    @Test
    public void shouldFilterStackTrace() {
        IOException exception = new IOException("Nested nested");
        exception.setStackTrace(new StackTraceElement[] {
                ioInternalMethod, ioApiMethod,
                nioInternalMethod, nioInternalMethod, nioInternalMethod, nioInternalMethod, nioInternalMethod,
                internalMethod, publicMethod, mainMethod
        });

        formatter.setPackageFilter(Arrays.asList("sun.nio.fs", "java.nio"));
        List<Map<String, Object>> stackTrace = formatter.createStackTrace(exception);
        Map<String, Object> frameAfterFilter = JsonUtil.getObject(stackTrace, 2);
        assertEquals(internalMethod.getMethodName(), frameAfterFilter.get("methodName"));
        assertEquals(5, frameAfterFilter.get("ignoredFrames"));
    }

    @Test
    public void shouldIncludeSourceLink() {
        IOException exception = new IOException("Nested nested");
        exception.setStackTrace(new StackTraceElement[]{
                new StackTraceElement("org.logevents.extend.servlets.JsonExceptionFormatter", "createStackTraceElement", "JsonExceptionFormatter.java", 44),
                new StackTraceElement("org.junit.internal.runners.model.ReflectiveCallable", "run", ".ReflectiveCallable.java", 12)
        });
        properties.put("formatter.sourceCode.1.package", "org.logevents");
        properties.put("formatter.sourceCode.1.github", "jhannes/logevents");
        JsonExceptionFormatter formatter = new JsonExceptionFormatter(properties, "formatter");

        List<Map<String, Object>> stackTrace = formatter.createStackTrace(exception);
        assertEquals("https://github.com/jhannes/logevents/blob/master/src/main/java/org/logevents/extend/servlets/JsonExceptionFormatter.java#L44",
                JsonUtil.getObject(stackTrace, 0).get("sourceLink"));
        assertNull(JsonUtil.getObject(stackTrace, 1).get("sourceLink"));
    }

    @Test
    public void shouldOutputNestedExceptions() {
        IOException ioException = new IOException("An IO exception happened");
        ioException.setStackTrace(new StackTraceElement[] {
                nioInternalMethod, nioApiMethod, internalMethod, publicMethod, mainMethod
        });

        RuntimeException exception = new RuntimeException("This is an error message", ioException);
        exception.setStackTrace(new StackTraceElement[] {
                internalMethod, publicMethod, mainMethod
        });

        String[] lines = getFormatter().format(exception).split("\r?\n");

        assertEquals("java.lang.RuntimeException: This is an error message", lines[0]);
        assertEquals("\tat " + internalMethod, lines[1]);
        assertEquals("Caused by: " + ioException, lines[4]);
        assertEquals("\tat " + nioInternalMethod, lines[5]);
        assertEquals("\tat " + nioApiMethod, lines[6]);
        assertEquals("\t... 3 more", lines[7]);
    }


    @Test
    public void shouldOutputSuppressedExceptions() {
        IOException nested = new IOException("Nested");
        nested.setStackTrace(new StackTraceElement[] {
                nioInternalMethod, nioInternalMethod, internalMethod, publicMethod, mainMethod
        });
        IOException nestedSuppressed = new IOException("Nested suppressed");
        nestedSuppressed.setStackTrace(new StackTraceElement[] {
                ioApiMethod, nioInternalMethod, internalMethod, publicMethod, mainMethod
        });
        nested.addSuppressed(nestedSuppressed);
        IOException suppressedSuppressed = new IOException("Suppressed, suppressed");
        suppressedSuppressed.setStackTrace(new StackTraceElement[] {
                ioInternalMethod, ioApiMethod, nioInternalMethod, internalMethod, publicMethod, mainMethod
        });
        nestedSuppressed.addSuppressed(suppressedSuppressed);

        String[] lines = getFormatter().format(nested).split("\r?\n");

        assertEquals(8, lines.length);
        assertEquals(nested.toString(), lines[0]);
        assertEquals("\tat " + nioInternalMethod, lines[1]);

        assertEquals("\tSuppressed: " + nestedSuppressed, lines[5]);
        assertEquals("\t\tat " + ioApiMethod, lines[6]);
        assertEquals("\t\t... 4 more", lines[7]);
    }

    @Test
    public void shouldLimitStackTrace() {
        IOException nestedNested = new IOException("Nested nested");
        nestedNested.setStackTrace(new StackTraceElement[] {
                ioInternalMethod, ioApiMethod, nioInternalMethod, nioInternalMethod, internalMethod, publicMethod, mainMethod
        });
        IOException nested = new IOException("Nested", nestedNested);
        nested.setStackTrace(new StackTraceElement[] {
                ioApiMethod, nioInternalMethod, nioInternalMethod, internalMethod, publicMethod, mainMethod
        });
        RuntimeException exception = new RuntimeException("This is an error message", nested);
        exception.setStackTrace(new StackTraceElement[] {
                internalMethod, publicMethod, mainMethod
        });

        properties.put("observer.file.formatter.exceptionFormatter.maxLength", "2");
        String[] lines = getFormatter().format(exception).split("\r?\n");

        assertEquals(exception.toString(), lines[0]);
        assertEquals("\tat " + internalMethod, lines[1]);
        assertEquals("\tat " + publicMethod, lines[2]);
        assertEquals("Caused by: " + nested, lines[3]);
        assertEquals("\tat " + ioApiMethod, lines[4]);
        assertEquals("\t[2 skipped]", lines[5]);
        assertEquals("Caused by: " + nestedNested, lines[6]);
        assertEquals("\tat " + ioInternalMethod, lines[7]);
        assertEquals("\t... 6 more", lines[8]);
        assertEquals(1 + 2 + 1 + 2 + 1 + 2, lines.length);
    }


    @Test
    public void shouldOutputFinalIgnoredLineCount() {
        IOException exceptions = new IOException("Nested nested");
        exceptions.setStackTrace(new StackTraceElement[] {
                ioInternalMethod, ioApiMethod,
                nioInternalMethod, nioInternalMethod, nioInternalMethod, nioInternalMethod, nioInternalMethod
        });

        properties.put("observer.file.formatter.exceptionFormatter.packageFilter",
                "sun.nio.fs, java.nio");
        String[] lines = getFormatter().format(exceptions).split("\r?\n");

        assertEquals(exceptions.toString(), lines[0]);
        assertEquals("\tat " + ioInternalMethod, lines[1]);
        assertEquals("\tat " + ioApiMethod, lines[2]);
        assertEquals("\t[5 skipped]", lines[3]);
        assertEquals(4, lines.length);

    }

    private ExceptionFormatter getFormatter() {
        Configuration configuration = new Configuration(properties, "observer.file.formatter");
        return configuration.createInstanceWithDefault("exceptionFormatter", ExceptionFormatter.class);
    }
}
