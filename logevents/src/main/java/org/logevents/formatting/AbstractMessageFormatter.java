package org.logevents.formatting;

import org.logevents.status.LogEventStatus;

import java.util.Arrays;

public abstract class AbstractMessageFormatter<OUTPUT> {

    protected abstract void outputArgument(OUTPUT result, Object arg);

    protected abstract void outputConstant(OUTPUT destination, CharSequence source, int start, int end);

    protected void format(OUTPUT result, String messageFormat, Object[] args) {
        if (args == null || args.length == 0) {
            outputConstant(result, messageFormat, 0, messageFormat.length());
            return;
        }
        int i = 0;
        int pos = 0;
        while (i < args.length) {
            int newPos;
            newPos = messageFormat.indexOf("{}", pos);
            while (isBackspace(messageFormat, newPos-1)) {
                outputConstant(result, messageFormat, pos, newPos-1);
                pos = newPos;
                if (isBackspace(messageFormat, newPos-2)) {
                    break;
                }
                newPos = messageFormat.indexOf("{}", pos+1);
            }
            if (newPos < 0) {
                break;
            }
            outputConstant(result, messageFormat, pos, newPos);
            safeOutputArgument(result, args[i]);
            pos = newPos + 2;
            i++;
        }

        outputConstant(result, messageFormat, pos, messageFormat.length());
    }


    private boolean isBackspace(String messageFormat, int pos) {
        return pos >= 0 && messageFormat.charAt(pos) == '\\';
    }

    private void safeOutputArgument(OUTPUT result, Object arg) {
        try {
            outputArgument(result, arg);
        } catch (Throwable t) {
            LogEventStatus.getInstance().addError(this,
                    "Failed toString() invocation on an object of type [" + arg.getClass().getName() + "]", t);
            outputArgument(result, "[FAILED toString()]");
        }
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
