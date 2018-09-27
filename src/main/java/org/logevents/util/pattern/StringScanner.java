package org.logevents.util.pattern;

import java.util.Optional;

import org.logevents.formatting.PatternLogEventFormatter;

/**
 * Utility method used to handle {@link PatternLogEventFormatter}
 * parsing. Keeps an internal string and a position. Use
 * {@link #current()} {@link #advance()} and {@link #hasMoreCharacters()}
 * to parse the string according to custom rules.
 *
 * @author Johannes Brodwall
 *
 */
public class StringScanner {

    private String string;
    private int position;

    public StringScanner(String string) {
        this.string = string;
        this.position = 0;
    }

    /**
     * Returns true if the current position is before the end of
     * the parsed string.
     */
    public boolean hasMoreCharacters() {
        return position < string.length();
    }

    /**
     * Returns the current character and advances the current position by one.
     */
    public char advance() {
        return string.charAt(position++);
    }

    /**
     * Returns the current character.
     */
    public char current() {
        return hasMoreCharacters() ? string.charAt(position) : '\0';
    }

    /**
     * Advances the current position over all current whitespace. If
     * the current position is not a whitespace when {@link #skipWhitespace()} is
     * called, this method has no effect.
     */
    public void skipWhitespace() {
        while (Character.isWhitespace(current())) {
            advance();
        }
    }

    /**
     * Advances the current position until the terminator is reached or
     * the position reaches the end of the string. The current position
     * remains at the terminator.
     *
     * @param terminator The character to scan for
     * @return The substring until the terminator, not including terminator
     */
    public String readUntil(char... terminatorsArray) {
        StringBuilder parameter = new StringBuilder();
        while (hasMoreCharacters()) {
            if (contains(terminatorsArray, current())) {
                break;
            }
            parameter.append(advance());
        }
        return parameter.toString();
    }

    private boolean contains(char[] array, char c) {
        for (int i = 0; i < array.length; i++) {
            if (array[i] == c) return true;
        }
        return false;
    }

    /**
     * Reads a signed integer. Returns {@link Optional#empty()} if the
     * current character isn't '-' or a digit.
     */
    public Optional<Integer> readInteger() {
        StringBuilder number = new StringBuilder();
        if (current() == '-') {
            number.append(advance());
        }
        if (!hasMoreCharacters()) {
            return Optional.empty();
        }
        while (Character.isDigit(current())) {
            number.append(advance());
        }
        if (number.length() > 0) {
            return Optional.of(Integer.parseInt(number.toString()));
        }
        return Optional.empty();
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{" + string + "}";
    }

}
