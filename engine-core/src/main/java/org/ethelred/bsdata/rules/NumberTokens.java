/* (C) Edward Harman and contributors 2026 */
package org.ethelred.bsdata.rules;

import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleUnaryOperator;
import org.jspecify.annotations.Nullable;

/**
 * The numbers inside a text value: in {@code D6+1+2} there are three (6, 1 and 2), in {@code 3+} one. Arithmetic and
 * clamps act on those numbers and leave the rest of the text alone (spec 5.6 and 5.7). A number is a run of digits, with
 * a decimal part if a point sits between digits, and with a minus sign if one comes directly before the digits, wherever
 * it is: in {@code D6-3} the numbers are 6 and -3 (observed in NR), so {@code increment 2} gives {@code D8-1}.
 */
final class NumberTokens {
    private NumberTokens() {}

    private record Token(int start, int end, double value) {}

    /**
     * Changes the numbers in {@code text} that {@code position} selects.
     *
     * <p>Without a position, or with 0 (which NR treats as absent), every number changes. A positive position counts
     * from the first number, starting at 1; a negative one counts from the last, so -1 is the last. A position outside
     * the numbers changes nothing. A number the change leaves as it was keeps its original text ({@code 007} stays
     * {@code 007} under {@code floor 5}). An empty value counts as the number 0 for arithmetic (observed:
     * {@code increment 2} gave {@code 2}) but is left empty by a clamp, which is why the caller says which it is; a value
     * with no numbers in it is left as it is.
     *
     * @param emptyIsZero whether an empty value is treated as 0
     */
    static String map(String text, @Nullable Integer position, boolean emptyIsZero, DoubleUnaryOperator change) {
        if (text.isEmpty()) {
            return emptyIsZero ? NumberText.format(change.applyAsDouble(0)) : text;
        }
        List<Token> tokens = find(text);
        if (tokens.isEmpty()) {
            return text;
        }
        int selected = select(tokens.size(), position);
        StringBuilder out = new StringBuilder();
        int at = 0;
        for (int i = 0; i < tokens.size(); i++) {
            Token t = tokens.get(i);
            out.append(text, at, t.start());
            boolean changes = position == null || position == 0 || i == selected;
            String original = text.substring(t.start(), t.end());
            double result = changes ? change.applyAsDouble(t.value()) : t.value();
            out.append(result == t.value() ? original : NumberText.format(result));
            at = t.end();
        }
        out.append(text.substring(at));
        return out.toString();
    }

    /** The index of the one number a non-zero position picks, or -1 if it picks none. */
    private static int select(int count, @Nullable Integer position) {
        if (position == null || position == 0) {
            return -1;
        }
        int index = position > 0 ? position - 1 : count + position;
        return index >= 0 && index < count ? index : -2;
    }

    private static List<Token> find(String s) {
        List<Token> out = new ArrayList<>();
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            boolean sign = c == '-' && i + 1 < s.length() && isDigit(s.charAt(i + 1));
            if (!isDigit(c) && !sign) {
                i++;
                continue;
            }
            int start = i;
            i += sign ? 1 : 0;
            while (i < s.length() && isDigit(s.charAt(i))) {
                i++;
            }
            if (i + 1 < s.length() && s.charAt(i) == '.' && isDigit(s.charAt(i + 1))) {
                i++;
                while (i < s.length() && isDigit(s.charAt(i))) {
                    i++;
                }
            }
            out.add(new Token(start, i, Double.parseDouble(s.substring(start, i))));
        }
        return out;
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }
}
