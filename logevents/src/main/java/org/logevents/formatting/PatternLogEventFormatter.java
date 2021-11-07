package org.logevents.formatting;

import org.logevents.LogEvent;
import org.logevents.config.Configuration;
import org.logevents.util.pattern.PatternConverterSpecWithSubpattern;
import org.logevents.util.pattern.PatternReader;
import org.slf4j.Marker;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/**
 * This class represents a {@link LogEventFormatter} which outputs the
 * {@link LogEvent} based on a configured pattern. The pattern consists of
 * constant parts and conversion parts, which starts with %-signs. The general format for conversion parts
 * is <code>%[&lt;minlength&gt;][.&lt;maxlength&gt;]&lt;conversion word&gt;[(&lt;conversion subpattern&gt;)][{&lt;parameter&gt;,&lt;parameter&gt;}]</code>.
 * Conversion parts are handled with {@link PatternConverterSpecWithSubpattern}. A conversion
 * is specified with a conversion word, and you can extend {@link PatternLogEventFormatter}
 * with your own conversion words by adding them to the ConverterBuilderFactory.
 * <p>
 * The following conversion words are supported:
 * <ul>
 *     <li><code>%logger</code>: The logger (category)</li>
 *     <li><code>%class</code>: The logging class (you should prefer %logger as it's more performant)</li>
 *     <li><code>%location</code>: The filename and line number in a format that's usually clickable, e.g. LogEvent.getSimpleCallerLocation(LogEvent.java:259)</li>
 *     <li><code>%file</code>: The logging class filename</li>
 *     <li><code>%line</code>: The line number of the logging call</li>
 *     <li>
 *         <code>%date</code>: The date and time of the log event.
 *         Optionally supports a date formatting pattern from {@link DateTimeFormatter#ofPattern}
 *         e.g. %date{DD-MMM HH:mm:ss}. Default format is <code>yyyy-MM-dd HH:mm:ss.SSS</code>.
 *     </li>
 *     <li><code>%time</code>: As %date, but with default format <code>HH:mm:ss.SSS</code></li>
 *     <li><code>%level</code></li>
 *     <li><code>%coloredLevel</code>: As level, but ERROR is bold red, WARN is red, and INFO is blue</li>
 *     <li><code>%message</code>: Message with arguments merged in</li>
 *     <li><code>%thread</code></li>
 *     <li><code>%marker</code>: {@link Marker} (if any)</li>
 *     <li>
 *         <code>%mdc</code>: will print all {@link org.slf4j.MDC} variables.
 *          Use %mdc{key:-default} to display a single mdc variable, or with {@link Configuration#getMdcFilter()}.
 *          <strong>If MDC values are printed, the MDC string is prefixed with a space</strong>
 *     </li>
 *     <li><code>%application</code>: The value of {@link Configuration#getApplicationName()}</li>
 *     <li><code>%node</code>: The value of {@link Configuration#getNodeName()} ()}</li>
 *     <li>
 *         <code>%highlight(&lt;subpattern&lt;)</code>:
 *         Highlights the subpattern based on the log level of the message: ERROR is bold red, WARN is red,
 *         and INFO is blue
 *     </li>
 *     <li>
 *          Colors: E.g. <code>%boldGreen(%thread)</code>.
 *          The following are supported: <code>%black(...)</code>, <code>%red(...)</code>, <code>%green(...)</code>
 *          <code>%yellow(...)</code>, <code>%blue(...)</code>, <code>%magenta(...)</code>, <code>%cyan(...)</code>,
 *          <code>%white(...)</code>,
 *          as well as <code>%bold(...)</code>, <code>%italic(...)</code>, <code>%underline(...)</code> and
 *          <code>%bold<em>color</em>(...)</code> for all colors
 *     </li>
 * </ul>
 *
 * Ansi colors will be used if running on a non-Windows shell or if
 * <a href="https://github.com/fusesource/jansi">JANSI</a> is in class path.
 * (Color on Windows is supported in IntelliJ, Cygwin and Ubuntu for Windows)
 *
 * @author Johannes Brodwall
 */
public class PatternLogEventFormatter implements LogEventFormatter {

    private static final LogEventFormatterBuilderFactory factory = new LogEventFormatterBuilderFactory();

    private static final ConsoleFormatting ansiFormat = ConsoleFormatting.getInstance();

    static {
        factory.put("logger", spec -> {
            Optional<Integer> length = spec.getIntParameter(0);
            return e -> e.getLoggerName(length);
        });
        factory.putAliases("logger", new String[] { "c", "lo" });

        factory.put("class", spec -> LogEvent::getCallerClassName); // TODO: int parameter
        factory.putAliases("class", new String[] { "C" });

        factory.put("method", spec -> LogEvent::getCallerMethodName);
        factory.putAliases("method", new String[] { "M" });

        factory.put("file", spec -> LogEvent::getCallerFileName);
        factory.putAliases("file", new String[] { "F" });

        factory.put("line", spec -> e -> String.valueOf(e.getCallerLine()));
        factory.putAliases("line", new String[] { "L" });

        factory.put("location", spec -> LogEvent::getSimpleCallerLocation);

        factory.put("date", spec -> {
            DateTimeFormatter formatter = spec.getParameter(0)
                    .map(DateTimeFormatter::ofPattern)
                    .orElse(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"));
            ZoneId zone = spec.getParameter(1)
                    .map(ZoneId::of)
                    .orElse(ZoneId.systemDefault());
            return e -> formatter.format(e.getInstant().atZone(zone));
        });
        factory.putAliases("date", new String[] { "d" });

        factory.put("time", spec -> {
            DateTimeFormatter formatter = spec.getParameter(0)
                    .map(DateTimeFormatter::ofPattern)
                    .orElse(DateTimeFormatter.ofPattern("HH:mm:ss.SSS"));
            ZoneId zone = spec.getParameter(1)
                    .map(ZoneId::of)
                    .orElse(ZoneId.systemDefault());
            return e -> formatter.format(e.getInstant().atZone(zone));
        });

        factory.put("level", spec -> e -> e.getLevel().toString());
        factory.put("coloredLevel", spec ->
                e -> ansiFormat.highlight(e.getLevel(), e.getLevel().toString()));
        factory.put("message", spec -> {
            MessageFormatter formatter = spec.getConfiguration().createInstanceWithDefault("messageFormatter", MessageFormatter.class);
            return event -> event.getMessage(formatter);
        });
        factory.putAliases("message", new String[] { "m", "msg" });
        factory.put("thread", spec -> LogEvent::getThreadName);
        factory.putAliases("thread", new String[] { "t" });
        factory.put("marker", spec -> e -> Optional.ofNullable(e.getMarker()).map(Marker::toString).orElse(""));

        factory.put("mdc", spec -> {
            if (spec.getParameters().isEmpty()) {
                MdcFilter mdcFilter = spec.getConfiguration().getMdcFilter();
                return e -> LogEventFormatter.mdc(e, mdcFilter);
            } else {
                String[] parts = spec.getParameters().get(0).split(":-");
                String key = parts[0];
                String defaultValue = parts.length > 1 ? parts[1] : "";
                return e -> e.getMdc(key, defaultValue);
            }
        });
        factory.putAliases("mdc", new String[] { "X" });

        factory.putTransformer("replace", spec -> {
            String regex = spec.getParameters().get(0);
            String replacement = spec.getParameters().get(1);
            return s -> s.replaceAll(regex, replacement);
        });

        factory.put("application", spec ->
                s -> spec.getConfiguration().getApplicationName()
        );
        factory.put("node", spec ->
                s -> spec.getConfiguration().getNodeName()
        );
        factory.put("applicationNode", spec ->
                s -> spec.getConfiguration().getApplicationName() + "@" + spec.getConfiguration().getNodeName()
        );


        // TODO

        //  relative / r - Outputs the number of milliseconds elapsed since the start of the application until the creation of the logging event.

        //  caller
        //  ?? property

        factory.put("highlight", spec -> {
            LogEventFormatter nestedFormatter = spec.getSubpattern().orElse(e -> "");
            return e -> ansiFormat.highlight(e.getLevel(), nestedFormatter.apply(e));
        });

        factory.putTransformer("black", spec -> ansiFormat::black);
        factory.putTransformer("red", spec -> ansiFormat::red);
        factory.putTransformer("green", spec -> ansiFormat::green);
        factory.putTransformer("yellow", spec -> ansiFormat::yellow);
        factory.putTransformer("blue", spec -> ansiFormat::blue);
        factory.putTransformer("magenta", spec -> ansiFormat::magenta);
        factory.putTransformer("cyan", spec -> ansiFormat::cyan);
        factory.putTransformer("white", spec -> ansiFormat::white);

        factory.putTransformer("boldBlack", spec -> ansiFormat::boldBlack);
        factory.putTransformer("boldRed", spec -> ansiFormat::boldRed);
        factory.putTransformer("boldGreen", spec -> ansiFormat::boldGreen);
        factory.putTransformer("boldYellow", spec -> ansiFormat::boldYellow);
        factory.putTransformer("boldBlue", spec -> ansiFormat::boldBlue);
        factory.putTransformer("boldMagenta", spec -> ansiFormat::boldMagenta);
        factory.putTransformer("boldCyan", spec -> ansiFormat::boldCyan);
        factory.putTransformer("boldWhite", spec -> ansiFormat::boldWhite);

        factory.putTransformer("bold", spec -> ansiFormat::bold);
        factory.putTransformer("italic", spec -> ansiFormat::italic);
        factory.putTransformer("underline", spec -> ansiFormat::underline);
    }

    private final Configuration configuration;

    private String pattern;
    private final ExceptionFormatter exceptionFormatter;

    private LogEventFormatter converter;

    public PatternLogEventFormatter(String pattern) {
        this(pattern, new Configuration());
    }

    public PatternLogEventFormatter(Map<String, String> properties, String prefix) {
        this(new Configuration(properties, prefix));
    }

    public PatternLogEventFormatter(Configuration configuration) {
        this.configuration = configuration;
        this.exceptionFormatter = configuration.createInstanceWithDefault("exceptionFormatter", ExceptionFormatter.class);
        setPattern(configuration.getString("pattern"));
        configuration.checkForUnknownFields();
    }

    public PatternLogEventFormatter(String pattern, Configuration configuration) {
        this.configuration = configuration;
        this.exceptionFormatter = configuration.createInstanceWithDefault("exceptionFormatter", ExceptionFormatter.class);
        configuration.checkForUnknownFields();
        setPattern(pattern);
    }

    public Collection<String> getConversionWords() {
        return factory.getConversionWords();
    }

    @Override
    public Optional<ExceptionFormatter> getExceptionFormatter() {
        return Optional.of(exceptionFormatter);
    }

    public void setPattern(String pattern) {
        this.pattern = pattern;
        this.converter = new PatternReader<>(configuration, factory).readPattern(pattern);
    }

    @Override
    public String apply(LogEvent event) {
        return converter.apply(event) + "\n" +exceptionFormatter.format(event.getThrowable());
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{" + pattern + "}";
    }

}
