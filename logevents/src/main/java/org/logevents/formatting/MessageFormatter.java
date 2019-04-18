package org.logevents.formatting;

import org.logevents.status.LogEventStatus;

import java.util.Arrays;

public class MessageFormatter {

    public String format(String messageFormat, Object... args) {
        if (args == null || args.length == 0) {
            return messageFormat;
        }

        StringBuilder result = new StringBuilder(messageFormat.length() + 50);
        format(result, messageFormat, args);
        return result.toString();
        //return org.slf4j.helpers.MessageFormatter.arrayFormat(messageFormat, args).getMessage();
    }

    private void format(StringBuilder result, String messageFormat, Object[] args) {
        int i = 0;
        int pos = 0;
        while (i < args.length) {
            int newPos;
            newPos = messageFormat.indexOf("{}", pos);
            while (isBackspace(messageFormat, newPos-1)) {
                result.append(messageFormat, pos, newPos-1);
                pos = newPos;
                if (isBackspace(messageFormat, newPos-2)) {
                    break;
                }
                newPos = messageFormat.indexOf("{}", pos+1);
            }
            if (newPos < 0) {
                break;
            }
            result.append(messageFormat, pos, newPos);
            safeOutputArgument(result, args[i]);
            pos = newPos + 2;
            i++;
        }

        result.append(messageFormat.substring(pos));
    }

    private boolean isBackspace(String messageFormat, int pos) {
        return pos >= 0 && messageFormat.charAt(pos) == '\\';
    }

    private void safeOutputArgument(StringBuilder result, Object arg) {
        try {
            outputArgument(result, arg);
        } catch (Throwable t) {
            LogEventStatus.getInstance().addError(this,
                    "Failed toString() invocation on an object of type [" + arg.getClass().getName() + "]", t);
            result.append("[FAILED toString()]");
        }
    }

    protected void outputArgument(StringBuilder result, Object arg) {
        result.append(toString(arg));
    }

    protected String toString(Object arg) {
        if (arg == null) {
            return null;
        }
        if (arg.getClass().isArray()) {
            return arrayAsString(arg);
        }
        return arg.toString();
    }

    protected String arrayAsString(Object arg) {
        if (arg.getClass() == byte[].class)
            return Arrays.toString((byte[]) arg);
        else if (arg.getClass() == short[].class)
            return Arrays.toString((short[]) arg);
        else if (arg.getClass() == int[].class)
            return Arrays.toString((int[]) arg);
        else if (arg.getClass() == long[].class)
            return Arrays.toString((long[]) arg);
        else if (arg.getClass() == char[].class)
            return Arrays.toString((char[]) arg);
        else if (arg.getClass() == float[].class)
            return Arrays.toString((float[]) arg);
        else if (arg.getClass() == double[].class)
            return Arrays.toString((double[]) arg);
        else if (arg.getClass() == boolean[].class)
            return Arrays.toString((boolean[]) arg);
        return Arrays.deepToString(((Object[])arg));
    }
}
