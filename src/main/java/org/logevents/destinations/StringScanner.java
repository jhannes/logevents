package org.logevents.destinations;

import java.util.Optional;

/**
 * Utility method used to handle {@link PatternLogEventFormatter}
 * parsing. Keeps an internal string and a position. Use
 * {@link #current()} {@link #advance()} and {@link #hasMoreCharacters()}
 * to parse the string according to custom rules.
 *
 * @author Johannes Brodwall
 *
 */
class StringScanner {

    private String string;
    private int position;

    public StringScanner(String string, int startPosition) {
        this.string = string;
        this.position = startPosition;
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
    public String readUntil(char terminator) {
        StringBuilder parameter = new StringBuilder();
        while (hasMoreCharacters()) {
            if (current() == terminator) {
                break;
            }
            parameter.append(advance());
        }
        return parameter.toString();
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
        while (hasMoreCharacters()) {
            if (!Character.isDigit(current())) break;
            number.append(advance());
        }
        if (number.length() > 0) {
            return Optional.of(Integer.parseInt(number.toString()));
        }
        return Optional.empty();
    }

}
