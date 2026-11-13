/* (C) Edward Harman and contributors 2026 */
package org.ethelred.bsdata.rules;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.ethelred.bsdata.roster.RosterModel;
import org.ethelred.bsdata.roster.Selection;
import org.ethelred.bsdata.roster.TreeNode;
import org.jspecify.annotations.Nullable;

/**
 * A modifier's {@code affects} path: which nodes it acts on instead of its holder, and which of their parts. The
 * grammar is {@code [self.][entries.][forces.][group.][recursive.][<filter>.]<target>} (spec 5.8). Which selections
 * the words reach was observed in New Recruit with units attached as leader and bodyguard (spec 6.3):
 *
 * <ul>
 *   <li>{@code self} is the holder (or the node its {@code scope} resolves to); it is also taken when the path has
 *       neither {@code self} nor {@code entries};
 *   <li>{@code entries} is the children, and with {@code recursive} everything below;
 *   <li>{@code group}, while the node is attached to another, is the whole group of attached units (the node itself
 *       first). Then no children are reached whatever else the path says, and the node itself and its group count only
 *       if the path has {@code self} or lacks {@code entries};
 *   <li>a filter keeps the selections that are the entry, link, category or type it names.
 * </ul>
 *
 * @param profileType for target {@code profiles}, the profile type's name (or id)
 */
public record Affects(
        boolean self,
        boolean entries,
        boolean forces,
        boolean group,
        boolean recursive,
        String filter,
        String target,
        String profileType) {

    private static final Set<String> FLAGS = Set.of("self", "entries", "forces", "group", "recursive");

    public static Affects parse(String path) {
        String[] t = path.split("\\.");
        boolean self = false;
        boolean entries = false;
        boolean forces = false;
        boolean group = false;
        boolean recursive = false;
        int i = 0;
        for (; i < t.length && FLAGS.contains(t[i]); i++) {
            switch (t[i]) {
                case "self" -> self = true;
                case "entries" -> entries = true;
                case "forces" -> forces = true;
                case "group" -> group = true;
                default -> recursive = true;
            }
        }
        String filter = "any";
        if (i < t.length && !isTarget(t[i]) && i + 1 < t.length) {
            filter = t[i++];
        }
        String target = i < t.length ? t[i++] : "entries";
        String rest = String.join(".", java.util.Arrays.asList(t).subList(Math.min(i, t.length), t.length));
        return new Affects(self, entries, forces, group, recursive, filter, target, rest);
    }

    private static boolean isTarget(String word) {
        return word.equals("profiles") || word.equals("rules") || word.startsWith("associations");
    }

    /** Whether the target is something the engine can apply: profiles' characteristics. */
    public boolean supported() {
        return target.equals("profiles") && !forces;
    }

    /** The selections this path reaches from {@code start}, holder-first where the group is concerned. */
    public List<Selection> selections(TreeNode start, RosterModel model) {
        List<Selection> out = new ArrayList<>();
        if (!(start instanceof Selection s)) {
            if (entries) {
                below(start, recursive, out);
            }
            return filtered(out);
        }
        boolean here = self || !entries;
        if (group && !model.group(s).equals(List.of(s))) {
            if (here) {
                out.addAll(model.group(s));
            }
        } else {
            if (here) {
                out.add(s);
            }
            if (entries) {
                below(s, recursive, out);
            }
        }
        return filtered(out);
    }

    private static void below(TreeNode node, boolean deep, List<Selection> out) {
        for (TreeNode c : node.down()) {
            if (c instanceof Selection s) {
                out.add(s);
            }
            if (deep || !(c instanceof Selection)) {
                below(c, deep, out);
            }
        }
    }

    private List<Selection> filtered(List<Selection> in) {
        if (filter.equals("any")) {
            return in;
        }
        List<Selection> out = new ArrayList<>();
        for (Selection s : in) {
            if (Evaluator.matches(s, filter)) {
                out.add(s);
            }
        }
        return out;
    }

    /** Whether a profile of this type is a target. */
    public boolean reaches(String typeName, String typeId, @Nullable String field) {
        return target.equals("profiles") && (profileType.equals(typeName) || profileType.equals(typeId));
    }
}
