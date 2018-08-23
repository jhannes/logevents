package org.logevents.formatting;

import java.util.Locale;

import org.fusesource.jansi.AnsiConsole;
import org.logevents.status.LogEventStatus;
import org.slf4j.event.Level;

/**
 * Returns ANSI colored strings unless unsupported. This
 * class will check if the JANSI project is loaded and if so,
 * use ANSI colors also on Windows. If loaded on Windows without
 * JANSI, the ANSI escape codes will be omitted.
 *
 * @author Johannes Brodwall
 *
 */
public class ConsoleFormatting {


    private static ConsoleFormatting instance;

    public synchronized static ConsoleFormatting getInstance() {
        if (instance == null) {
            if (!isWindows() || isUnixShell()) {
                instance = new ConsoleFormatting();
            } else {
                try {
                    Class.forName("org.fusesource.jansi.AnsiConsole");
                    AnsiConsole.systemInstall();
                    instance = new ConsoleFormatting();
                } catch (ClassNotFoundException e) {
                    LogEventStatus.getInstance().addInfo(ConsoleFormatting.class, "Could not load jansi - color output not supported on Windows ");
                    instance = nullConsoleFormatting();
                }
            }
        }
        return instance;
    }

    private static ConsoleFormatting nullConsoleFormatting() {
        return new ConsoleFormatting() {
            @Override
            protected String ansi(String s, Color color, Format format) {
                return s;
            }
        };
    }

    private static boolean isUnixShell() {
        return System.getenv("PWD") != null && System.getenv("PWD").startsWith("/");
    }

    private static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase(Locale.ENGLISH).contains("win");
    }

    enum Color {
        BLACK(30), RED(31), GREEN(32), YELLOW(33), BLUE(34), MAGENTA(35), CYAN(36), WHITE(37);

        private final int code;

        private Color(int code) {
            this.code = code;
        }
    }

    enum Format {
        BOLD(1), UNDERLINE(4);

        private final int code;

        Format(int code) {
            this.code = code;
        }
    }

    public String black(String s) {
        return ansi(s, Color.BLACK);
    }

    public String magenta(String s) {
        return ansi(s, Color.MAGENTA);
    }

    public String white(String s) {
        return ansi(s, Color.WHITE);
    }

    public String cyan(String s) {
        return ansi(s, Color.CYAN);
    }

    public String red(String s) {
        return ansi(s, Color.RED);
    }

    public String green(String s) {
        return ansi(s, Color.GREEN);
    }

    public String yellow(String s) {
        return ansi(s, Color.YELLOW);
    }

    public String blue(String s) {
        return ansi(s, Color.BLUE);
    }

    public String bold(String s) {
        return ansi(s, null, Format.BOLD);
    }

    public String boldBlack(String s) {
        return ansi(s, Color.BLACK, Format.BOLD);
    }

    public String boldRed(String s) {
        return ansi(s, Color.RED, Format.BOLD);
    }

    public String boldGreen(String s) {
        return ansi(s, Color.GREEN, Format.BOLD);
    }

    public String boldYellow(String s) {
        return ansi(s, Color.YELLOW, Format.BOLD);
    }

    public String boldBlue(String s) {
        return ansi(s, Color.BLUE, Format.BOLD);
    }

    public String boldMagenta(String s) {
        return ansi(s, Color.MAGENTA, Format.BOLD);
    }

    public String boldCyan(String s) {
        return ansi(s, Color.CYAN, Format.BOLD);
    }

    public String boldWhite(String s) {
        return ansi(s, Color.WHITE, Format.BOLD);
    }

    /**
     * Output ANSI color coded string, where ERROR is bold red, WARN is
     * red, INFO is blue and other levels are default color.
     */
    public String highlight(Level level, String s) {
        if (level == Level.ERROR) {
            return boldRed(s);
        } else if (level == Level.WARN) {
            return red(s);
        } else if (level == Level.INFO) {
            return blue(s);
        }
        return s;
    }

    protected final String ansi(String s, Color color) {
        return ansi(s, color, null);
    }

    protected String ansi(String s, Color color, Format format) {
        if (format == null) {
            return String.format("\033[%sm%s\033[m", color.code, s);
        } else if (color == null) {
            return String.format("\033[%s;m%s\033[m", format.code, s);
        } else {
            return String.format("\033[%s;%sm%s\033[m", format.code, color.code, s);
        }
    }
}
