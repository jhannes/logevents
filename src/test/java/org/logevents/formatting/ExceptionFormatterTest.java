package org.logevents.formatting;

import static org.junit.Assert.assertEquals;

import java.io.IOException;

import org.junit.Test;

public class ExceptionFormatterTest {

    private StackTraceElement mainMethod = new StackTraceElement("org.logeventsdemo.MyApplication", "main", "MyApplication.java", 20);
    private StackTraceElement publicMethod = new StackTraceElement("org.logeventsdemo.internal.MyClassName", "publicMethod", "MyClassName.java", 31);
    private StackTraceElement internalMethod = new StackTraceElement("org.logeventsdemo.internal.MyClassName", "internalMethod", "MyClassName.java", 311);
    private StackTraceElement nioApiMethod = new StackTraceElement("java.nio.file.Files", "write", "Files.java", 3292);
    private StackTraceElement nioInternalMethod = new StackTraceElement("sun.nio.fs.WindowsException", "translateToIOException", "WindowsException.java", 79);
    private StackTraceElement ioApiMethod = new StackTraceElement("java.io.FilterOutputStream", "close", "FilterOutputStream.java", 180);
    private StackTraceElement ioInternalMethod = new StackTraceElement("java.io.FileOutputStream", "close", "FileOutputStream.java", 323);

    private ExceptionFormatter formatter = new ExceptionFormatter();

    @Test
    public void shouldFormatStackTrace() {
        RuntimeException exception = new RuntimeException("This is an error message");
        exception.setStackTrace(new StackTraceElement[] {
                internalMethod, publicMethod, mainMethod
        });

        String[] lines = formatter.format(exception, 100).split("\r?\n");
        assertEquals("java.lang.RuntimeException: This is an error message", lines[0]);
        assertEquals("\tat org.logeventsdemo.internal.MyClassName.internalMethod(MyClassName.java:311)", lines[1]);
        assertEquals("\tat org.logeventsdemo.internal.MyClassName.publicMethod(MyClassName.java:31)", lines[2]);
        assertEquals("\tat org.logeventsdemo.MyApplication.main(MyApplication.java:20)", lines[3]);
        assertEquals(4, lines.length);
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

        String[] lines = formatter.format(exception, 100).split("\r?\n");

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

        String[] lines = formatter.format(nested, 100).split("\r?\n");

        assertEquals(nested.toString(), lines[0]);
        assertEquals("\tat " + nioInternalMethod, lines[1]);

        assertEquals("\tSuppressed: " + nestedSuppressed, lines[6]);
        assertEquals("\t\tat " + ioApiMethod, lines[7]);
        assertEquals("\t\t... 4 more", lines[8]);

        assertEquals("\t\tSuppressed: " + suppressedSuppressed, lines[9]);
        assertEquals("\t\t\tat " + ioInternalMethod, lines[10]);
        assertEquals("\t\t\t... 5 more", lines[11]);
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

        String[] lines = formatter.format(exception, 2).split("\r?\n");

        assertEquals(exception.toString(), lines[0]);
        assertEquals("\tat " + internalMethod, lines[1]);
        assertEquals("\tat " + publicMethod, lines[2]);
        assertEquals("Caused by: " + nested, lines[3]);
        assertEquals("\tat " + ioApiMethod, lines[4]);
        assertEquals("\tat " + nioInternalMethod, lines[5]);
        assertEquals("Caused by: " + nestedNested, lines[6]);
        assertEquals("\tat " + ioInternalMethod, lines[7]);
        assertEquals("\t... 6 more", lines[8]);
        assertEquals(1 + 2 + 1 + 2 + 1 + 2, lines.length);
    }


}
