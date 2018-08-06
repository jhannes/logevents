package org.logevents.formatting;

import static org.junit.Assert.assertEquals;

import java.io.IOException;

import org.junit.Test;

public class ExceptionFormatterTest {

    private StackTraceElement mainMethod = new StackTraceElement("org.logeventsdemo.MyApplication", "main", "MyApplication.java", 20);
    private StackTraceElement publicMethod = new StackTraceElement("org.logeventsdemo.internal.MyClassName", "publicMethod", "MyClassName.java", 31);
    private StackTraceElement internalMethod = new StackTraceElement("org.logeventsdemo.internal.MyClassName", "internalMethod", "MyClassName.java", 311);
    private StackTraceElement ioApiMethod = new StackTraceElement("java.nio.file.Files", "write", "Files.java", 3292);
    private StackTraceElement ioInternalMethod = new StackTraceElement("sun.nio.fs.WindowsException", "translateToIOException", "WindowsException.java", 79);

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
    public void shouldLimitStackTrace() {
        RuntimeException exception = new RuntimeException("This is an error message");
        exception.setStackTrace(new StackTraceElement[] {
                internalMethod, publicMethod, mainMethod
        });

        String[] lines = formatter.format(exception, 2).split("\r?\n");
        assertEquals(1 + 2, lines.length);
        assertEquals(exception.toString(), lines[0]);
        assertEquals("\tat " + internalMethod, lines[1]);
        assertEquals("\tat " + publicMethod, lines[2]);
    }

    @Test
    public void shouldOutputNestedExceptions() {
        IOException ioException = new IOException("An IO exception happened");
        ioException.setStackTrace(new StackTraceElement[] {
                ioInternalMethod, ioApiMethod, internalMethod, publicMethod, mainMethod
        });

        RuntimeException exception = new RuntimeException("This is an error message", ioException);
        exception.setStackTrace(new StackTraceElement[] {
                internalMethod, publicMethod, mainMethod
        });

        String[] lines = formatter.format(exception, 100).split("\r?\n");
        assertEquals("java.lang.RuntimeException: This is an error message", lines[0]);
        assertEquals("\tat " + internalMethod, lines[1]);
        assertEquals("Caused by: " + ioException, lines[4]);
        assertEquals("\tat " + ioInternalMethod, lines[5]);
        assertEquals("\tat " + ioApiMethod, lines[6]);
    }


}
