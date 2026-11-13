/* (C) Edward Harman and contributors 2026 */
package org.ethelred.bsdata.rules;

import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Applies modifiers to one field's value. It knows nothing of rosters: it is given the modifiers that apply and how many
 * times each applies, and returns the new value. The order of application is by type, whatever the order the modifiers
 * were listed in (spec 5.12): set, add, remove and the primary-category types; then append and prepend; then arithmetic;
 * then floor and ceil; then the cumulative types and replace. Within a stage, listing order is kept.
 *
 * <p>Numbers are plain numbers, or a number with a trailing {@code +} such as a save of {@code 3+}. A value with
 * several numbers in it (a dice expression such as {@code D6+1}) is not handled yet (milestone M4): the modifier is
 * skipped and a diagnostic says so.
 */
public final class ModifierEngine {
    /** A modifier with the number of times it applies. Zero disables it. */
    public record Applied(Modifier modifier, double times) {}

    private ModifierEngine() {}

    /** The stage of a type: lower runs first. */
    static int stage(String type) {
        return switch (type) {
            case "set", "add", "remove", "set-primary", "unset-primary" -> 1;
            case "append", "prepend" -> 2;
            case "increment", "decrement", "multiply", "divide", "modulo", "power", "exponent", "triangular" -> 3;
            case "floor", "ceil" -> 4;
            case "cumulative-add", "cumulative-multiply", "cumulative-power", "replace" -> 5;
            default -> 6;
        };
    }

    private static List<Applied> ordered(List<Applied> mods) {
        List<Applied> out = new ArrayList<>(mods);
        out.sort((a, b) ->
                Integer.compare(stage(a.modifier().type()), stage(b.modifier().type())));
        return out; // List.sort is stable, so listing order is kept within a stage
    }

    /** Applies the modifiers to a text field such as a characteristic. */
    public static String applyText(String base, List<Applied> mods, List<String> diagnostics) {
        String current = base;
        for (Applied a : ordered(mods)) {
            Modifier m = a.modifier();
            if (a.times() == 0) {
                continue; // a repeat that counts nothing disables the modifier; a negative count still applies
            }
            switch (m.type()) {
                case "set" -> current = m.valueText();
                case "append" -> current = join(current, m, true);
                case "prepend" -> current = join(current, m, false);
                case "replace" -> current = replace(current, m);
                case "add", "remove", "set-primary", "unset-primary" ->
                    diagnostics.add("modifier type " + m.type() + " on a characteristic: not supported");
                default -> {
                    PlainNumber n = PlainNumber.parse(current);
                    Double v = m.valueNumber();
                    if (n == null || v == null) {
                        diagnostics.add("modifier " + m.type() + " on \"" + current
                                + "\": only plain numbers and N+ values are handled until M4");
                        continue;
                    }
                    Double result = arithmetic(m.type(), n.number(), v, a.times(), diagnostics);
                    if (result != null) {
                        current = NumberText.format(result) + n.suffix();
                    }
                }
            }
        }
        return current;
    }

    /** Applies the modifiers to a numeric field such as a cost. */
    public static double applyNumber(double base, List<Applied> mods, List<String> diagnostics) {
        double current = base;
        for (Applied a : ordered(mods)) {
            Modifier m = a.modifier();
            if (a.times() == 0) {
                continue;
            }
            Double v = m.valueNumber();
            if (m.type().equals("set")) {
                if (v == null) {
                    diagnostics.add("set on a number with a value that is not a number");
                } else {
                    current = v;
                }
                continue;
            }
            if (v == null) {
                diagnostics.add("modifier " + m.type() + " on a number needs a numeric value");
                continue;
            }
            Double result = arithmetic(m.type(), current, v, a.times(), diagnostics);
            if (result != null) {
                current = result;
            }
        }
        return current;
    }

    private static String join(String current, Modifier m, boolean after) {
        String text = m.valueText();
        String skip = m.skipIfPresent();
        // honoured on append and prepend only (observed in NR)
        if (skip != null && !skip.isEmpty() && current.contains(skip)) {
            return current;
        }
        if (current.isEmpty()) {
            return text; // no leading separator on an empty field
        }
        String joiner = m.join();
        String sep = joiner == null ? " " : joiner;
        return after ? current + sep + text : text + sep + current;
    }

    /** Literal, every occurrence; a missing value deletes; an empty arg sets the value only on an empty field. */
    private static String replace(String current, Modifier m) {
        String found = m.arg();
        String arg = found == null ? "" : found;
        String value = m.valueText();
        if (arg.isEmpty()) {
            return current.isEmpty() ? value : current;
        }
        return current.replace(arg, value);
    }

    /** One arithmetic or clamp step with {@code k} repetitions, by the formulas fitted in spec 5.6. */
    private static @Nullable Double arithmetic(String type, double n, double v, double k, List<String> diagnostics) {
        switch (type) {
            case "increment":
                return n + v * k;
            case "decrement":
                return n - v * k;
            case "multiply":
            case "exponent":
                return n * Math.pow(v, k);
            case "divide":
                return n / Math.pow(v, k);
            case "power":
                return Math.pow(n, Math.pow(v, k));
            case "modulo": {
                double r = n;
                for (int i = 0; i < (int) k; i++) {
                    r = r % v;
                }
                return r;
            }
            case "triangular":
                return n + v * k * (k + 1) / 2;
            case "floor":
                return n < v ? v : n;
            case "ceil":
                return n > v ? v : n;
            case "cumulative-add":
                return n + v * (k + 1) / 2;
            case "cumulative-multiply": {
                double sum = 0;
                double p = 1;
                for (int i = 1; i <= (int) k; i++) {
                    p *= v;
                    sum += p;
                }
                return n * sum;
            }
            case "cumulative-power": {
                double sum = 0;
                double p = 1;
                for (int i = 0; i < (int) k; i++) {
                    sum += p;
                    p *= v;
                }
                return n * sum / k;
            }
            default:
                diagnostics.add("modifier type " + type + ": not supported");
                return null;
        }
    }

    /** A plain number, or a number with a trailing plus. */
    record PlainNumber(double number, String suffix) {
        static @Nullable PlainNumber parse(String s) {
            String digits = s;
            String suffix = "";
            if (s.endsWith("+")) {
                digits = s.substring(0, s.length() - 1);
                suffix = "+";
            }
            if (digits.isEmpty() || !digits.equals(digits.trim())) {
                return null;
            }
            for (int i = 0; i < digits.length(); i++) {
                char c = digits.charAt(i);
                boolean ok = c >= '0' && c <= '9' || c == '.' || (c == '-' && i == 0);
                if (!ok) {
                    return null;
                }
            }
            try {
                return new PlainNumber(Double.parseDouble(digits), suffix);
            } catch (NumberFormatException e) {
                return null;
            }
        }
    }
}
