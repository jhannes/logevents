package org.logevents.destinations;

import java.util.Optional;

public class StringScanner {

    private String string;
    private int position;

    public StringScanner(String string, int startPosition) {
        this.string = string;
        this.position = startPosition;
    }

    public boolean hasMoreCharacters() {
        return position < string.length();
    }

    public char advance() {
        return string.charAt(position++);
    }

    public char current() {
        return hasMoreCharacters() ? string.charAt(position) : '\0';
    }

    public void skipWhitespace() {
        while (Character.isWhitespace(current())) {
            advance();
        }
    }

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
