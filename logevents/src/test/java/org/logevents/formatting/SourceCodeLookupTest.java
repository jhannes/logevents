package org.logevents.formatting;

import org.junit.Test;
import org.logevents.observers.batch.SlackExceptionFormatter;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Optional;
import java.util.Properties;

import static org.junit.Assert.*;

public class SourceCodeLookupTest {

    private final Properties properties = new Properties();

    @Test
    public void shouldLinkToSourceCode() {
        properties.put("observer.file.formatter.sourceCode.1.package", "org.logevents");
        properties.put("observer.file.formatter.sourceCode.1.github", "jhannes/logevents");

        StackTraceElement stackFrame = new StackTraceElement(getClass().getName(), "shouldLinkToSourceCode", "ExceptionFormatterTest.java", 235);
        String sourceLink = getSourceCodeLookup().getSourceLink(stackFrame);
        assertEquals(
                "https://github.com/jhannes/logevents/blob/master/src/main/java/org/logevents/formatting/SourceCodeLookupTest.java#L235",
                sourceLink);
    }

    @Test
    public void shouldLinkToGithubModule() {
        SourceCodeLookup sourceCodeLookup = new SourceCodeLookup();
        sourceCodeLookup.addPackageGithubLocation("org.logevents", "jhannes/logevents/logevents", Optional.empty());

        StackTraceElement stackFrame = new StackTraceElement(getClass().getName(), "shouldLinkToGithubModule", "ExceptionFormatterTest.java", 248);
        String sourceLink = sourceCodeLookup.getSourceLink(stackFrame);
        assertEquals(
                "https://github.com/jhannes/logevents/blob/master/logevents/src/main/java/org/logevents/formatting/SourceCodeLookupTest.java#L248",
                sourceLink);
    }

    @Test
    public void shouldConfigureDefaultLinkToSourceCode() {
        properties.put("observer.*.sourceCode.1.package", "org.logevents");
        properties.put("observer.*.sourceCode.1.github", "jhannes/logevents");

        StackTraceElement stackFrame = new StackTraceElement(getClass().getName(), "shouldConfigureDefaultLinkToSourceCode", "ExceptionFormatterTest.java", 251);
        String sourceLink = getSourceCodeLookup().getSourceLink(stackFrame);
        assertEquals(
                "https://github.com/jhannes/logevents/blob/master/src/main/java/org/logevents/formatting/SourceCodeLookupTest.java#L251",
                sourceLink);
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