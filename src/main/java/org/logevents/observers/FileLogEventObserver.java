package org.logevents.observers;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.logevents.LogEvent;
import org.logevents.LogEventObserver;
import org.logevents.formatting.LogEventFormatter;
import org.logevents.formatting.PatternConverterSpec;
import org.logevents.formatting.TTLLEventLogFormatter;
import org.logevents.util.Configuration;
import org.logevents.util.StringScanner;

public class FileLogEventObserver implements LogEventObserver {

    protected final Path path;
    private final Function<LogEvent, String> filenameGenerator;
    private final LogEventFormatter formatter;
    private final FileDestination destination;


    public FileLogEventObserver(Properties properties, String prefix) {
        this(new Configuration(properties, prefix));
    }

    public FileLogEventObserver(Configuration configuration) {
        this(configuration.createInstanceWithDefault("formatter", LogEventFormatter.class, TTLLEventLogFormatter.class),
                Paths.get(configuration.getString("filename")));
    }

    public FileLogEventObserver(LogEventFormatter formatter, Path path) {
        this.formatter = formatter;
        this.path = path;
        this.filenameGenerator = parseFilenameGenerator(path.getFileName().toString());
        destination = new FileDestination(path.getParent());
    }

    @Override
    public void logEvent(LogEvent logEvent) {
        destination.writeEvent(getFilename(logEvent), formatter.format(logEvent) + "\n");
    }

    protected String getFilename(LogEvent logEvent) {
        return filenameGenerator.apply(logEvent);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName()
                + "{filename=" + path
                + ",formatter=" + formatter + "}";
    }


    private static Map<String, Function<PatternConverterSpec, Function<LogEvent, String>>> factory = new HashMap<>();

    static {
        factory.put("date", spec -> {
            DateTimeFormatter formatter = spec.getParameter(0)
                    .map(DateTimeFormatter::ofPattern)
                    .orElse(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            ZoneId zone = spec.getParameter(1)
                    .map(ZoneId::of)
                    .orElse(ZoneId.systemDefault());
            return e -> formatter.format(Instant.now().atZone(zone));
        });

        factory.put("d", factory.get("date"));
    }

    private static Function<LogEvent, String> parseFilenameGenerator(String filenamePattern) {
        StringScanner scanner = new StringScanner(filenamePattern);

        List<Function<LogEvent, String>> converters = new ArrayList<>();
        while (scanner.hasMoreCharacters()) {
            // TODO: Escaped %
            String text = scanner.readUntil('%');
            if (text.length() > 0) {
                converters.add(getConstant(text));
            }
            if (scanner.hasMoreCharacters()) {
                PatternConverterSpec patternSpec = new PatternConverterSpec(scanner);
                patternSpec.readConversion();
                converters.add(createPattern(patternSpec));
            }
        }

        return compositeFormatter(converters);
    }

    private static Function<LogEvent, String> compositeFormatter(List<Function<LogEvent, String>> converters) {
        return e -> converters.stream()
                .map(converter -> converter.apply(e))
                .collect(Collectors.joining(""));
    }

    private static Function<LogEvent, String> createPattern(PatternConverterSpec patternSpec) {
        if (!factory.containsKey(patternSpec.getConversionWord())) {
            throw new IllegalArgumentException("Unknown conversion <%" + patternSpec + "> not in " + factory.keySet());
        }
        return factory.get(patternSpec.getConversionWord()).apply(patternSpec);
    }

    private static Function<LogEvent, String> getConstant(String string) {
        return e -> string;
    }

}
