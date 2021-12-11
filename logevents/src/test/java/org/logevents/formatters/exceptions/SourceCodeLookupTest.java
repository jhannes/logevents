package org.logevents.formatters.exceptions;

import org.junit.Test;
import org.logevents.config.Configuration;
import org.logevents.formatters.SourceCodeLookup;
import org.logevents.formatters.exceptions.ExceptionFormatter;
import org.logevents.observers.slack.SlackExceptionFormatter;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SourceCodeLookupTest {

    private final Map<String, String> properties = new HashMap<>();

    @Test
    public void shouldLinkToSourceCode() {
        properties.put("observer.file.formatter.sourceCode.1.package", "org.logevents");
        properties.put("observer.file.formatter.sourceCode.1.github", "jhannes/logevents");

        StackTraceElement stackFrame = new StackTraceElement(getClass().getName(), "shouldLinkToSourceCode", "ExceptionFormatterTest.java", 235);
        String sourceLink = getSourceCodeLookup().getSourceLink(stackFrame);
        assertEquals(
                "https://github.com/jhannes/logevents/blob/master/src/main/java/org/logevents/formatters/exceptions/SourceCodeLookupTest.java#L235",
                sourceLink);
    }

    @Test
    public void shouldLinkToGithubModule() {
        SourceCodeLookup sourceCodeLookup = new SourceCodeLookup();
        sourceCodeLookup.addPackageGithubLocation("org.logevents", "jhannes/logevents/logevents", Optional.empty());

        StackTraceElement stackFrame = new StackTraceElement(getClass().getName(), "shouldLinkToGithubModule", "ExceptionFormatterTest.java", 248);
        String sourceLink = sourceCodeLookup.getSourceLink(stackFrame);
        assertEquals(
                "https://github.com/jhannes/logevents/blob/master/logevents/src/main/java/org/logevents/formatters/exceptions/SourceCodeLookupTest.java#L248",
                sourceLink);
    }

    @Test
    public void shouldConfigureDefaultLinkToSourceCode() {
        properties.put("observer.*.sourceCode.1.package", "org.logevents");
        properties.put("observer.*.sourceCode.1.github", "jhannes/logevents");

        StackTraceElement stackFrame = new StackTraceElement(getClass().getName(), "shouldConfigureDefaultLinkToSourceCode", "ExceptionFormatterTest.java", 251);
        String sourceLink = getSourceCodeLookup().getSourceLink(stackFrame);
        assertEquals(
                "https://github.com/jhannes/logevents/blob/master/src/main/java/org/logevents/formatters/exceptions/SourceCodeLookupTest.java#L251",
                sourceLink);
    }

    public static class MySourceCode extends SourceCodeLookup {
        private final List<String> packages;

        public MySourceCode(Map<String, String> properties, String prefix) {
            super(properties, prefix);
            this.packages = new Configuration(properties, prefix).getStringList("packages");
        }

        @Override
        public String getSourceLink(StackTraceElement stackTraceElement) {
            for (String aPackage : packages) {
                if (stackTraceElement.getClassName().startsWith(aPackage)) {
                    return "http://example.com/ABC";
                }
            }

            return super.getSourceLink(stackTraceElement);
        }
    }

    @Test
    public void shouldConfigureGlobalSourceLinkClass() {
        properties.put("observer.*.sourceCode", MySourceCode.class.getName());
        properties.put("observer.*.sourceCode.packages", "net.example");
        ExceptionFormatter formatter = new ExceptionFormatter(properties, "observer.test.formatter");
        StackTraceElement frame = new StackTraceElement("net.example.hello.MainClas",
                "main", "MainClass.java", 35);
        assertEquals("http://example.com/ABC", formatter.sourceCodeLookup.getSourceLink(frame));
    }

    @Test
    public void shouldConfigureSourceLinkClass() {
        properties.put("observer.test.formatter.sourceCode", MySourceCode.class.getName());
        properties.put("observer.test.formatter.sourceCode.packages", "net.example");
        ExceptionFormatter formatter = new ExceptionFormatter(properties, "observer.test.formatter");
        StackTraceElement frame = new StackTraceElement("net.example.hello.MainClas",
                "main", "MainClass.java", 35);
        assertEquals("http://example.com/ABC", formatter.sourceCodeLookup.getSourceLink(frame));
    }

    @Test
    public void shouldReadSourceLinkFromPomFile() throws IOException, ParserConfigurationException, SAXException {
        SourceCodeLookup formatter = new SourceCodeLookup();
        try (FileInputStream pomResource = new FileInputStream("../pom.xml")) {
            formatter.addPackageMavenLocation("org.logevents", pomResource);
        }
        StackTraceElement frame = new StackTraceElement(SlackExceptionFormatter.class.getName(),
                "addPackageMavenLocation", "SlackExceptionFormatter.java", 35);
        formatter.getSourceLink(frame);

        assertContains("https://github.com/jhannes/logevents/", formatter.getSourceLink(frame));
    }

    private void assertContains(String expected, String actual) {
        assertTrue("Expected <" + actual + "> to contain <" + expected + ">",
                actual.contains(expected));
    }

    private SourceCodeLookup getSourceCodeLookup() {
        return new SourceCodeLookup(properties, "observer.file.formatter.sourceCode");
    }
}
