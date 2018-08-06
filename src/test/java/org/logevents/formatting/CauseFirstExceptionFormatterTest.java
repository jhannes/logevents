package org.logevents.formatting;

import static org.junit.Assert.assertEquals;

import java.io.IOException;

import org.junit.Test;

public class CauseFirstExceptionFormatterTest {

    private StackTraceElement mainMethod = new StackTraceElement("org.logeventsdemo.MyApplication", "main", "MyApplication.java", 20);
    private StackTraceElement publicMethod = new StackTraceElement("org.logeventsdemo.internal.MyClassName", "publicMethod", "MyClassName.java", 31);
    private StackTraceElement internalMethod = new StackTraceElement("org.logeventsdemo.internal.MyClassName", "internalMethod", "MyClassName.java", 311);
    private StackTraceElement nioInternalMethod = new StackTraceElement("sun.nio.fs.WindowsException", "translateToIOException", "WindowsException.java", 79);
    private StackTraceElement ioApiMethod = new StackTraceElement("java.io.FilterOutputStream", "close", "FilterOutputStream.java", 180);
    private StackTraceElement ioInternalMethod = new StackTraceElement("java.io.FileOutputStream", "close", "FileOutputStream.java", 323);

    private CauseFirstExceptionFormatter formatter = new CauseFirstExceptionFormatter();

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

        String[] lines = formatter.format(exception, 100).split("\r?\n");

        assertEquals(nestedNested.toString(), lines[0]);
        assertEquals("\tat " + ioInternalMethod, lines[1]);
        assertEquals("\t... 5 more", lines[2]);
        assertEquals("Wrapped by: " + nested, lines[3]);
        assertEquals("\tat " + ioApiMethod, lines[4]);
        assertEquals("\tat " + nioInternalMethod, lines[5]);
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

        String[] lines = formatter.format(wrapping, 100).split("\r?\n");

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


}