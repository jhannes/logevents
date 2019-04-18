package org.logevents.formatting;

public class ConsoleMessageFormatter extends MessageFormatter {
    private ConsoleFormatting format;

    public ConsoleMessageFormatter(ConsoleFormatting format) {
        this.format = format;
    }

    @Override
    protected void outputArgument(StringBuilder result, Object arg) {
        result.append(format.underline(toString(arg)));
    }
}
