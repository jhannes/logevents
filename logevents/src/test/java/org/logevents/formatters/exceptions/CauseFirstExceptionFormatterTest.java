package org.logevents.formatters.exceptions;

import org.junit.Test;
import org.logevents.config.Configuration;
import org.logevents.formatters.exceptions.CauseFirstExceptionFormatter;
import org.logevents.formatters.exceptions.ExceptionFormatter;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class CauseFirstExceptionFormatterTest {

    private final StackTraceElement mainMethod = new StackTraceElement("org.logeventsdemo.MyApplication", "main", "MyApplication.java", 20);
    private final StackTraceElement publicMethod = new StackTraceElement("org.logeventsdemo.internal.MyClassName", "publicMethod", "MyClassName.java", 31);
    private final StackTraceElement internalMethod = new StackTraceElement("org.logeventsdemo.internal.MyClassName", "internalMethod", "MyClassName.java", 311);
    private final StackTraceElement nioInternalMethod = new StackTraceElement("sun.nio.fs.WindowsException", "translateToIOException", "WindowsException.java", 79);
    private final StackTraceElement ioApiMethod = new StackTraceElement("java.io.FilterOutputStream", "close", "FilterOutputStream.java", 180);
    private final StackTraceElement ioInternalMethod = new StackTraceElement("java.io.FileOutputStream", "close", "FileOutputStream.java", 323);

    private final Map<String, String> properties = new HashMap<>();
    {
        properties.put("observer.file.formatter.exceptionFormatter",
                CauseFirstExceptionFormatter.class.getName());
    }

    @Test
    public void shouldFormatSimpleException() {
        IOException simpleException = new IOException("This went wrong");
        simpleException.setStackTrace(new StackTraceElement[] { publicMethod, mainMethod });

        String nl = ExceptionFormatter.newLine();
        assertEquals("java.io.IOException: This went wrong" + nl +  "\tat " + publicMethod + nl + "\tat " + mainMethod + nl,
                getFormatter().format(simpleException));
    }

    @Test
    public void shouldFormatExceptionWithRootCauseFirst() {
        IOException nestedNested = new IOException("Nested nested");
        nestedNested.setStackTrace(new StackTraceElement[] {
                ioInternalMethod, ioApiMethod, nioInternalMethod, internalMethod, publicMethod, mainMethod
        });
        IOException nested = new IOException("Nested", nestedNested);
        nested.setStackTrace(new StackTraceElement[] {
                ioApiMethod, nioInternalMethod, internalMethod, publicMethod, mainMethod
        });
        RuntimeException exception = new RuntimeException("This is an error message", nested);
        exception.setStackTrace(new StackTraceElement[] {
                internalMethod, publicMethod, mainMethod
        });

        String[] lines = getFormatter().format(exception).split("\r?\n");

        assertEquals(nestedNested.toString(), lines[0]);
        assertEquals("\tat " + ioInternalMethod, lines[1]);
        assertEquals("\t... 5 more", lines[2]);
        assertEquals("Wrapped by: " + nested, lines[3]);
        assertEquals("\tat " + ioApiMethod, lines[4]);
        assertEquals("\t[1 skipped]", lines[5]);
        assertEquals("\t... 3 more", lines[6]);
        assertEquals("Wrapped by: " + exception, lines[7]);
        assertEquals("\tat " + internalMethod, lines[8]);
        assertEquals("\tat " + publicMethod, lines[9]);
        assertEquals("\tat " + mainMethod, lines[10]);
    }


    @Test
    public void shouldOutputSuppressedExceptions() {
        IOException nestedSuppressed = new IOException("Suppressed, suppressed");
        nestedSuppressed.setStackTrace(new StackTraceElement[] {
                ioInternalMethod, ioApiMethod, nioInternalMethod, internalMethod, publicMethod, mainMethod
        });
        IOException suppressed = new IOException("Nested suppressed", nestedSuppressed);
        suppressed.setStackTrace(new StackTraceElement[] {
                ioApiMethod, nioInternalMethod, internalMethod, publicMethod, mainMethod
        });

        IOException wrapping = new IOException("Nested and suppressed");
        wrapping.setStackTrace(new StackTraceElement[] {
                nioInternalMethod, internalMethod, publicMethod, mainMethod
        });
        wrapping.addSuppressed(suppressed);

        String[] lines = getFormatter().format(wrapping).split("\r?\n");

        assertEquals(wrapping.toString(), lines[0]);
        assertEquals("\tat " + nioInternalMethod, lines[1]);
        assertEquals("\tat " + internalMethod, lines[2]);
        assertEquals("\tat " + publicMethod, lines[3]);
        assertEquals("\tat " + mainMethod, lines[4]);

        assertEquals("\tSuppressed: " + nestedSuppressed, lines[5]);
        assertEquals("\t\tat " + ioInternalMethod, lines[6]);
        assertEquals("\t\t... 5 more", lines[7]);

        assertEquals("\tWrapped by: " + suppressed, lines[8]);
        assertEquals("\t\tat " + ioApiMethod, lines[9]);
    }


    private ExceptionFormatter getFormatter() {
        Configuration configuration = new Configuration(properties, "observer.file.formatter");
        return configuration.createInstance("exceptionFormatter", ExceptionFormatter.class);
    }

}
