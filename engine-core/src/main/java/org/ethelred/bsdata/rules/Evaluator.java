/* (C) Edward Harman and contributors 2026 */
package org.ethelred.bsdata.rules;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.ethelred.bsdata.roster.CategoryNode;
import org.ethelred.bsdata.roster.ForceNode;
import org.ethelred.bsdata.roster.RosterModel;
import org.ethelred.bsdata.roster.Selection;
import org.ethelred.bsdata.roster.TreeNode;
import org.jspecify.annotations.Nullable;

/**
 * Evaluates queries and conditions against a roster tree. Behaviour follows the spec (sections 5.9, 5.12 and 6.1);
 * where the spec records an observation that looks like a bug in New Recruit (the ordinary form of {@code before}, and
 * an unset {@code min} on a count group acting like 1) that behaviour is reproduced, as the project decided
 * ("compat mode"). Anything not supported is recorded in {@link #diagnostics()} and treated as disabled.
 *
 * <p>Costs come from {@link CostSource}. M2 uses base costs; the modifier pipeline of M3 supplies evaluated ones.
 */
public final class Evaluator {
    /**
     * The outcome of a condition. {@code DISABLED} is for things the engine does not support (a condition type or group
     * type it cannot evaluate, an {@code instanceOf} whose scope resolves to nothing). A count whose scope resolves to
     * nothing is not disabled: New Recruit compares it as 0 (observed).
     */
    public enum Result {
        TRUE,
        FALSE,
        DISABLED
    }

    /**
     * The cost of <em>one</em> of a selection (not times its amount, and not its children's) as a query should see it.
     * Queries multiply by how many of it they count, relative to the scope node.
     */
    public interface CostSource {
        double unitCost(Selection selection, String costTypeId);
    }

    private final RosterModel model;
    private final CostSource costs;
    private final Set<String> diagnostics = new LinkedHashSet<>();

    public Evaluator(RosterModel model) {
        this(model, (s, type) -> s.unitCosts().getOrDefault(type, 0.0));
    }

    public Evaluator(RosterModel model, CostSource costs) {
        this.model = model;
        this.costs = costs;
    }

    /** What could not be evaluated so far, each once, in the order met. */
    public List<String> diagnostics() {
        return new ArrayList<>(diagnostics);
    }

    // ---- queries

    /**
     * The value of a query seen from {@code here}, or null if its scope resolves to nothing (the condition is then
     * disabled) or the query is not supported.
     */
    public @Nullable Double count(TreeNode here, Query q) {
        List<String> unsupported = new ArrayList<>();
        Scopes.Found found = Scopes.resolve(here, q.scope(), model, unsupported);
        diagnostics.addAll(unsupported);
        if (found == null) {
            return null;
        }
        List<Scopes.Found> founds = founds(here, found, q);
        Double raw = sum(founds, q, q.childId());
        if (raw == null || !q.percentValue()) {
            return raw;
        }
        Double all = sum(founds, q, "any");
        return all == null || all == 0 ? Double.valueOf(0) : Double.valueOf(raw / all);
    }

    /**
     * The scope node, or with {@code traverseAssociationGroup} the nodes the scope finds from each unit in the
     * attached group, each once (observed: a force counted for a leader and its bodyguard is counted once).
     */
    private List<Scopes.Found> founds(TreeNode here, Scopes.Found found, Query q) {
        if (!q.traverseAssociationGroup() || !(here instanceof Selection s)) {
            return List.of(found);
        }
        List<Scopes.Found> out = new ArrayList<>();
        List<TreeNode> seen = new ArrayList<>();
        for (Selection member : model.group(s)) {
            Scopes.Found f = member == s ? found : Scopes.resolve(member, q.scope(), model, new ArrayList<>());
            if (f == null || f.kind() != Scopes.Kind.NODE) {
                continue;
            }
            TreeNode node = f.nodes().get(0);
            if (seen.stream().noneMatch(x -> x == node)) {
                seen.add(node);
                out.add(f);
            }
        }
        return out;
    }

    private @Nullable Double sum(List<Scopes.Found> founds, Query q, String childId) {
        double total = 0;
        for (Scopes.Found f : founds) {
            Double c = countFrom(f, q.field(), childId, q);
            if (c == null) {
                return null;
            }
            total += c;
        }
        return total;
    }

    private @Nullable Double countFrom(Scopes.Found found, String field, String childId, Query q) {
        if (field.startsWith("limit::")) {
            if (found.nodes().get(0) instanceof RosterModel) {
                return model.costLimit(field.substring("limit::".length()));
            }
            diagnostics.add("limit queries are only valid at roster scope");
            return null;
        }
        if (field.equals("associations")) {
            // observed: New Recruit counted nothing for this field in every form tried, so a min always fails and a
            // max never does
            return 0.0;
        }
        if (found.chain()) {
            diagnostics.add("scope ancestor: counting is not modelled (New Recruit's counts depend on the host in ways"
                    + " not yet understood); instanceOf works");
            return null;
        }
        boolean cost = !field.equals("selections") && !field.equals("forces");
        // kept from New Recruit: scope self with the scope node's own entry id as childId counts like "any"
        String effective = childId;
        if (q.scope().equals("self") && found.nodes().get(0) instanceof Selection self && matchesIds(self, childId)) {
            effective = "any";
        }
        // observed: a count in a force reaches its child forces only with includeChildForces (the roster reaches all
        // forces); for costs the spec says includeChildForces is implied unless the scope is a force
        boolean forces = q.includeChildForces() || cost && !q.scope().equals("force");
        List<Candidate> candidates = candidates(found, q.includeChildSelections() || cost, !cost, forces);
        double sum = 0;
        for (Candidate c : candidates) {
            if (field.equals("forces")) {
                if (c.node() instanceof ForceNode f && matches(f, effective)) {
                    sum += 1;
                }
            } else if (c.node() instanceof Selection s && matches(s, effective)) {
                sum += field.equals("selections") ? c.weight() : costs.unitCost(s, field) * c.weight();
            }
        }
        return sum;
    }

    private static boolean matchesIds(Selection s, String id) {
        if (s.entry().id().equals(id)) {
            return true;
        }
        var link = s.link();
        return link != null && (link.id().equals(id) || link.targetId().equals(id));
    }

    /** A node to be counted, with how many of it there are relative to the scope node (amounts below the scope). */
    private record Candidate(TreeNode node, double weight) {}

    /**
     * What a scope node counts. Amounts are relative to the scope node: a Qu of 2 inside a Qm of 3 counts as 2 from the
     * Qm, not 6 (observed). Forces and categories are transparent: the direct children of a roster are the selections
     * at the top of its forces. With {@code -self} the node itself counts, at its own amount, except for costs.
     *
     * @param deep all depths, or only the next level
     * @param selfCounts whether {@code -self} adds the node (false for cost queries)
     * @param includeForces whether to look inside child forces of a force (a child force is itself a candidate for
     *     {@code forces} either way; the roster looks inside every force)
     */
    private static List<Candidate> candidates(
            Scopes.Found found, boolean deep, boolean selfCounts, boolean includeForces) {
        List<Candidate> out = new ArrayList<>();
        switch (found.kind()) {
            case MEMBERS -> {
                for (TreeNode m : found.nodes()) {
                    Selection s = (Selection) m;
                    out.add(new Candidate(s, s.amount()));
                    if (deep) {
                        collect(s, s.amount(), true, includeForces, out);
                    }
                }
            }
            case CHAIN -> {
                for (TreeNode n : found.nodes()) {
                    out.add(new Candidate(n, 1));
                }
            }
            case NODE -> {
                TreeNode node = found.nodes().get(0);
                if (found.includeSelf() && selfCounts) {
                    out.add(new Candidate(node, node instanceof Selection s ? s.amount() : 1));
                }
                collect(node, 1, deep, includeForces || node instanceof RosterModel, out);
            }
        }
        return out;
    }

    private static void collect(
            TreeNode node, double weight, boolean deep, boolean includeForces, List<Candidate> out) {
        for (TreeNode child : node.down()) {
            if (child instanceof ForceNode) {
                // structure, not something to count by amount, but a candidate for "forces" queries; its contents
                // only count when child forces are included
                out.add(new Candidate(child, 1));
                if (includeForces) {
                    collect(child, weight, deep, true, out);
                }
                continue;
            }
            if (child instanceof CategoryNode) {
                collect(child, weight, deep, includeForces, out);
                continue;
            }
            double w = weight * (child instanceof Selection s ? s.amount() : 1);
            out.add(new Candidate(child, w));
            if (deep) {
                collect(child, w, true, includeForces, out);
            }
        }
    }

    /** The selections a scope finds, at the next level or at all depths (the node itself is not included). */
    static List<Selection> selectionsIn(Scopes.Found found, boolean deep) {
        List<Selection> out = new ArrayList<>();
        for (Candidate c : candidates(found, deep, false, false)) {
            if (c.node() instanceof Selection s) {
                out.add(s);
            }
        }
        return out;
    }

    static boolean matches(Selection s, String childId) {
        return switch (childId) {
            case "any" -> true;
            case "unit", "model", "upgrade", "mount", "crew" -> s.type().equals(childId);
            default -> s.is(childId);
        };
    }

    private static boolean matches(ForceNode f, String childId) {
        return childId.equals("any")
                || f.entry().id().equals(childId)
                || f.catalogue().id().equals(childId);
    }

    // ---- conditions

    /** All of the owner's conditions, groups and local groups must hold. Disabled members are skipped. */
    public Result eval(TreeNode here, Conditions cs) {
        List<Result> results = new ArrayList<>();
        cs.conditions().forEach(c -> results.add(eval(here, c)));
        cs.groups().forEach(g -> results.add(eval(here, g)));
        cs.localGroups().forEach(g -> results.add(eval(here, g)));
        return all(results);
    }

    private static Result all(List<Result> results) {
        boolean any = false;
        for (Result r : results) {
            if (r == Result.FALSE) {
                return Result.FALSE;
            }
            any |= r == Result.TRUE;
        }
        return results.isEmpty() || any ? Result.TRUE : Result.DISABLED;
    }

    public Result eval(TreeNode here, Condition c) {
        return eval(here, here, c);
    }

    /**
     * @param outerHere the node the whole condition sits on; only {@code before} inside a local group uses it
     */
    private Result eval(TreeNode here, TreeNode outerHere, Condition c) {
        switch (c.type()) {
            case "always":
                return Result.TRUE;
            case "never":
                return Result.FALSE;
            case "instanceOf":
            case "notInstanceOf":
                return instanceOf(here, c);
            case "before":
                return before(here, outerHere, c);
            default:
                break;
        }
        Double counted = count(here, Conditions.query(c));
        // a scope that resolves to nothing counts as 0, so "at most 0" holds and "at least 1" does not (observed)
        double count = counted == null ? 0 : counted;
        Boolean ok = compare(c.type(), count, c.value());
        if (ok == null) {
            diagnostics.add("condition type " + c.type() + ": not supported");
            return Result.DISABLED;
        }
        // kept from BattleScribe: atLeast at scope self with a count of 0 is false even for a value of 0
        if (c.type().equals("atLeast") && c.scope().equals("self") && count == 0) {
            return Result.FALSE;
        }
        return ok ? Result.TRUE : Result.FALSE;
    }

    private static @Nullable Boolean compare(String type, double count, double value) {
        return switch (type) {
            case "atLeast" -> count >= value;
            case "atMost" -> count <= value;
            case "lessThan" -> count < value;
            case "greaterThan" -> count > value;
            case "equalTo" -> count == value;
            case "notEqualTo" -> count != value;
            default -> null;
        };
    }

    private Result instanceOf(TreeNode here, Condition c) {
        List<String> unsupported = new ArrayList<>();
        Scopes.Found found = Scopes.resolve(here, c.scope(), model, unsupported);
        diagnostics.addAll(unsupported);
        if (found == null) {
            return Result.DISABLED;
        }
        boolean is = false;
        for (TreeNode n : found.nodes()) {
            is |= n instanceof Selection s && matches(s, c.childId())
                    || n instanceof ForceNode f && matches(f, c.childId());
        }
        return is == c.type().equals("instanceOf") ? Result.TRUE : Result.FALSE;
    }

    /**
     * {@code before}. Inside a local group, where the data uses it, the candidate (here) must come earlier in the tree
     * than the node the condition sits on, and match the {@code childId}. As an ordinary condition New Recruit was
     * observed to treat it as "exists": true whenever something matching is in scope, including the node itself and
     * nodes after it. That contradicts its own notes and is probably unintended, but it is what the engine reproduces.
     */
    private Result before(TreeNode here, TreeNode outerHere, Condition c) {
        if (here != outerHere) {
            if (here instanceof Selection cand && outerHere instanceof Selection outer) {
                boolean earlier = model.order(cand) < model.order(outer);
                return earlier && matches(cand, c.childId()) ? Result.TRUE : Result.FALSE;
            }
            return Result.FALSE;
        }
        Double count = count(here, Conditions.query(c));
        if (count == null) {
            return Result.DISABLED;
        }
        return count > 0 ? Result.TRUE : Result.FALSE;
    }

    public Result eval(TreeNode here, ConditionGroup g) {
        return eval(here, here, g);
    }

    private Result eval(TreeNode here, TreeNode outerHere, ConditionGroup g) {
        List<Result> members = new ArrayList<>();
        g.conditions().forEach(c -> members.add(eval(here, outerHere, c)));
        g.groups().forEach(sub -> members.add(eval(here, outerHere, sub)));
        g.localGroups().forEach(l -> members.add(eval(here, l)));
        int trues = 0;
        boolean anyEnabled = false;
        for (Result r : members) {
            trues += r == Result.TRUE ? 1 : 0;
            anyEnabled |= r != Result.DISABLED;
        }
        if (!members.isEmpty() && !anyEnabled) {
            return Result.DISABLED;
        }
        boolean ok;
        switch (g.type()) {
            case "and":
                ok = trues == enabled(members);
                break;
            case "or":
                ok = members.isEmpty() || trues > 0;
                break;
            case "not":
                ok = trues == 0;
                break;
            case "count":
                // min and max are inclusive; an unset min acts like 1 (observed in New Recruit)
                Double lo = g.min();
                Double hi = g.max();
                double min = lo == null ? 1 : lo;
                double max = hi == null ? Double.POSITIVE_INFINITY : hi;
                ok = trues >= min && trues <= max;
                break;
            default:
                diagnostics.add("condition group type " + g.type() + ": not supported");
                return Result.DISABLED;
        }
        return ok ? Result.TRUE : Result.FALSE;
    }

    private static int enabled(List<Result> members) {
        int n = 0;
        for (Result r : members) {
            n += r == Result.DISABLED ? 0 : 1;
        }
        return n;
    }

    /**
     * Counts the selections in the scope for which every inner condition holds (each acting as "self"), then compares
     * that count with the group's value.
     */
    public Result eval(TreeNode here, LocalGroup g) {
        List<String> unsupported = new ArrayList<>();
        Scopes.Found found = Scopes.resolve(here, g.scope(), model, unsupported);
        diagnostics.addAll(unsupported);
        if (!g.field().equals("selections")) {
            diagnostics.add("local group field " + g.field() + ": not supported");
            return Result.DISABLED;
        }
        double count = 0;
        // a scope that resolves to nothing has nothing to count: the count is 0, as for any other condition
        List<Candidate> candidates =
                found == null ? List.of() : candidates(found, g.includeChildSelections(), true, g.includeChildForces());
        for (Candidate cd : candidates) {
            if (!(cd.node() instanceof Selection s)) {
                continue;
            }
            boolean all = true;
            for (Condition c : g.conditions()) {
                all &= eval(s, here, c) == Result.TRUE;
            }
            for (ConditionGroup sub : g.groups()) {
                all &= eval(s, here, sub) == Result.TRUE;
            }
            if (all) {
                count += cd.weight();
            }
        }
        Boolean ok = compare(g.type(), count, g.value());
        if (ok == null) {
            diagnostics.add("local group type " + g.type() + ": not supported");
            return Result.DISABLED;
        }
        return ok ? Result.TRUE : Result.FALSE;
    }

    // ---- repeats

    /**
     * How many times a repeat says a modifier applies: the count divided by its value, rounded down (up if
     * {@code roundUp}), times {@code repeats}. Null if the query's scope resolves to nothing.
     */
    public @Nullable Double times(TreeNode here, Repeat r) {
        Double count = count(here, r.query());
        if (count == null) {
            return null;
        }
        if (r.value() == 0) {
            return 0.0;
        }
        double ratio = count / r.value();
        return (r.roundUp() ? Math.ceil(ratio) : Math.floor(ratio)) * r.repeats();
    }
}
