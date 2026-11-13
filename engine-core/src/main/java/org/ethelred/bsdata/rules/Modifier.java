/* (C) Edward Harman and contributors 2026 */
package org.ethelred.bsdata.rules;

import java.util.ArrayList;
import java.util.List;
import org.ethelred.bsdata.DataNode;
import org.jspecify.annotations.Nullable;

/**
 * A modifier: changes one field when its conditions hold, as many times as its repeats say. See
 * {@code docs/bsdata-extensions-spec.md}, section 5, for the types and what each does.
 *
 * @param value what to set, add or compare with; a string, a number or a boolean, absent for a {@code replace} that
 *     deletes
 * @param arg the text a {@code replace} looks for
 * @param join the separator for {@code append} and {@code prepend}; null means the default, a single space
 * @param position which number inside a string an arithmetic type changes (M4)
 * @param skipIfPresent text that stops an {@code append} or {@code prepend} when the field already contains it
 * @param affects a path to other nodes the modifier acts on instead of its holder (M7)
 */
public record Modifier(
        String type,
        String field,
        @Nullable DataNode value,
        @Nullable String arg,
        @Nullable String join,
        @Nullable Integer position,
        @Nullable String skipIfPresent,
        @Nullable String affects,
        @Nullable String scope,
        Conditions conditions,
        List<Repeat> repeats) {

    public static List<Modifier> of(List<DataNode> modifiers) {
        List<Modifier> out = new ArrayList<>();
        for (DataNode m : modifiers) {
            Json j = Json.of(m);
            Double pos = j.optNum("position");
            out.add(new Modifier(
                    j.str("type", "set"),
                    j.str("field", ""),
                    value(m),
                    j.optStr("arg"),
                    j.optStr("join"),
                    pos == null ? null : Integer.valueOf((int) Math.round(pos)),
                    j.optStr("skipIfPresent"),
                    j.optStr("affects"),
                    j.optStr("scope"),
                    Conditions.of(m),
                    Conditions.repeats(m)));
        }
        return out;
    }

    private static @Nullable DataNode value(DataNode modifier) {
        return modifier instanceof DataNode.Obj o ? o.fields().get("value") : null;
    }

    /** The value as text: strings as they are, numbers without a needless decimal point, absent as empty. */
    public String valueText() {
        DataNode v = value;
        return switch (v) {
            case DataNode.Str s -> s.value();
            case DataNode.Num n -> NumberText.format(n.value());
            case DataNode.Bool b -> String.valueOf(b.value());
            case null, default -> "";
        };
    }

    /** The value as a number, or null if it is absent or not numeric (a numeric string is accepted). */
    public @Nullable Double valueNumber() {
        DataNode v = value;
        if (v instanceof DataNode.Num n) {
            return n.value();
        }
        if (v instanceof DataNode.Str s) {
            try {
                return Double.valueOf(Double.parseDouble(s.value().trim()));
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    public boolean hasValue() {
        return value != null && !(value instanceof DataNode.Null);
    }

    /** A modifier group: conditions and repeats that apply to every modifier in it, and its members. */
    public record Group(Conditions conditions, List<Repeat> repeats, List<Modifier> modifiers, List<Group> groups) {
        public static List<Group> of(List<DataNode> groups) {
            List<Group> out = new ArrayList<>();
            for (DataNode g : groups) {
                List<DataNode> mods = new ArrayList<>();
                List<DataNode> nested = new ArrayList<>();
                if (g instanceof DataNode.Obj o) {
                    if (o.fields().get("modifiers") instanceof DataNode.Arr a) {
                        mods.addAll(a.items());
                    }
                    if (o.fields().get("modifierGroups") instanceof DataNode.Arr a) {
                        nested.addAll(a.items());
                    }
                }
                out.add(new Group(Conditions.of(g), Conditions.repeats(g), Modifier.of(mods), Group.of(nested)));
            }
            return out;
        }
    }
}
