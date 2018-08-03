package org.logevents.destinations;

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

    public int getPosition() {
        return position;
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

}
