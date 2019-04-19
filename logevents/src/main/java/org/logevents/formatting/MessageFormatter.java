package org.logevents.formatting;

/**
 * Output a message format with embedded message arguments. This is a reimplementation of
 * {@link org.slf4j.helpers.MessageFormatter}, which allows for custom formatting of
 * the embedded message arguments.
 */
public class MessageFormatter extends AbstractMessageFormatter<StringBuilder> {

    public String format(String messageFormat, Object... args) {
        if (args == null || args.length == 0) {
            return messageFormat;
        }

        StringBuilder result = new StringBuilder(messageFormat.length() + 50);
        format(result, messageFormat, args);
        return result.toString();
    }

    @Override
    protected void outputConstant(StringBuilder destination, CharSequence source, int start, int end) {
        destination.append(source, start, end);
    }

    @Override
    protected void outputArgument(StringBuilder result, Object arg) {
        result.append(toString(arg));
    }

}
