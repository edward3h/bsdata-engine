/* (C) Edward Harman and contributors 2026 */
package org.ethelred.bsdata.rules;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.ethelred.bsdata.model.Body;
import org.ethelred.bsdata.model.Child;
import org.ethelred.bsdata.model.Entry;
import org.ethelred.bsdata.model.GameData;
import org.ethelred.bsdata.model.Link;
import org.ethelred.bsdata.roster.RosterModel;
import org.ethelred.bsdata.roster.Selection;
import org.jspecify.annotations.Nullable;

/**
 * Checks a roster against the constraints in its data (spec section 6.2) and the roster's cost limits. Behaviour is
 * what was observed in New Recruit with the synthetic {@code M5 Constraints} catalogue:
 *
 * <ul>
 *   <li>a constraint counts with the same query as a condition; {@code min} fails below the limit, {@code max} above
 *       it, {@code exactly} on any difference; a limit of -1 means none unless {@code negative} is set; with
 *       {@code percentValue} the limit is a percentage of the count of everything in the scope;
 *   <li>an entry's constraints are checked for each selection of it, and also for an entry that is available under a
 *       selection but not selected (a {@code min} on a child fails while the child is absent), as if a child of
 *       amount 0 were asked;
 *   <li>a group's constraints are checked under each selection that has the group, with {@code self} meaning the
 *       group's members;
 *   <li>a {@code min} constraint on a hidden entry does not fire; a selection of a hidden entry is itself a problem;
 *   <li>a constraint's own modifiers (field = its id) change its limit.
 * </ul>
 *
 * Not yet: {@code associations}, {@code traverseAssociationGroup} (M7), {@code shared: false}, and constraints on
 * forces and categories (M6). Those are reported in {@link #diagnostics()} and skipped.
 */
public final class Validator {
    private final RosterModel model;
    private final View view;
    private final Evaluator evaluator;
    private final Set<String> diagnostics = new LinkedHashSet<>();
    private final Map<Body, List<Constraint>> parsed = new IdentityHashMap<>();
    private final List<Violation> out = new ArrayList<>();

    public Validator(RosterModel model, View view) {
        this.model = model;
        this.view = view;
        this.evaluator = view.evaluator();
    }

    /** What could not be checked so far, each once, in the order met. */
    public List<String> diagnostics() {
        return new ArrayList<>(diagnostics);
    }

    /** Every violation: selections in tree order (own constraints, then available entries and groups), then the roster. */
    public List<Violation> validate() {
        out.clear();
        for (Selection s : model.selections()) {
            for (Constraint c : constraints(s)) {
                check(s, c, s, false);
            }
            Selection parent = s.parent();
            if (parent != null && view.hidden(s)) {
                out.add(new Violation(
                        parent,
                        Violation.Kind.HIDDEN,
                        null,
                        1,
                        0,
                        parent.name() + " " + s.name() + " cannot be selected while hidden"));
            }
            available(s, s.entry().body().children(), s);
            var link = s.link();
            if (link != null) {
                available(s, link.body().children(), s);
            }
        }
        costLimits();
        return new ArrayList<>(out);
    }

    // ---- available entries and groups

    private void available(Selection host, List<Child> children, Selection attach) {
        for (Child child : children) {
            Entry entry;
            Link link = null;
            switch (child) {
                case Entry e -> entry = e;
                case Link l -> {
                    GameData.Resolved target = model.data().target(host.file(), l);
                    if (target == null) {
                        continue;
                    }
                    entry = target.entry();
                    link = l;
                }
            }
            if (entry.group()) {
                checkGroup(host, child, entry, link);
                available(host, entry.body().children(), attach);
                if (link != null) {
                    available(host, link.body().children(), attach);
                }
            } else if (!selected(host, entry)) {
                Selection probe = model.probe(host, child);
                if (probe != null) {
                    for (Constraint c : constraints(probe)) {
                        // observed: an absent entry's min and exactly fire, its max does not (nothing is selected)
                        if (!c.type().equals("max")) {
                            check(probe, c, host, false);
                        }
                    }
                }
            }
        }
    }

    private static boolean selected(Selection host, Entry entry) {
        for (Selection c : host.children()) {
            if (c.entry().id().equals(entry.id())) {
                return true;
            }
        }
        return false;
    }

    /** A group's constraints, with {@code self} meaning its members, asked from a probe of its first member. */
    private void checkGroup(Selection host, Child child, Entry group, @Nullable Link link) {
        List<Constraint> mine = new ArrayList<>(constraints(group.body()));
        if (link != null) {
            mine.addAll(constraints(link.body()));
        }
        if (mine.isEmpty()) {
            return;
        }
        Selection probe = firstMember(host, group);
        if (probe == null) {
            return;
        }
        for (Constraint c : mine) {
            check(probe, c, host, true);
        }
    }

    private @Nullable Selection firstMember(Selection host, Entry group) {
        for (Child c : group.body().children()) {
            if (c instanceof Entry e && e.group()) {
                Selection nested = firstMember(host, e);
                if (nested != null) {
                    return nested;
                }
            } else {
                Selection p = model.probe(host, c);
                if (p != null && !p.entry().group()) {
                    return p;
                }
            }
        }
        return null;
    }

    // ---- one constraint

    private List<Constraint> constraints(Selection s) {
        List<Constraint> out = new ArrayList<>(constraints(s.entry().body()));
        var link = s.link();
        if (link != null) {
            out.addAll(constraints(link.body()));
        }
        return out;
    }

    private List<Constraint> constraints(Body body) {
        List<Constraint> cached = parsed.get(body);
        if (cached == null) {
            cached = body.raw().isEmpty() ? List.of() : Constraint.of(body.raw().constraints());
            parsed.put(body, cached);
        }
        return cached;
    }

    private void check(Selection here, Constraint c, Selection attach, boolean group) {
        if (c.traverseAssociationGroup() || c.field().equals("associations")) {
            diagnostics.add("constraint on associations or across an attached group: not supported until M7");
            return;
        }
        if (!c.shared()) {
            diagnostics.add("constraint with shared false: not supported");
            return;
        }
        if (c.type().equals("min") && view.hidden(here)) {
            return; // a min on a hidden entry does not fire (observed)
        }
        double limit = view.constraintValue(here, c);
        if (limit == -1 && !c.negative()) {
            return; // no limit
        }
        Query q = c.query(here.entry().id());
        if (group && q.scope().equals("self")) {
            q = new Query(
                    q.field(),
                    "group",
                    q.childId(),
                    q.includeChildSelections(),
                    q.includeChildForces(),
                    q.percentValue());
        }
        Double counted = evaluator.count(here, q);
        double count = counted == null ? 0 : counted;
        if (c.percentValue()) {
            count *= 100;
        }
        switch (c.type()) {
            case "min" -> {
                if (count < limit) {
                    add(here, c, attach, Violation.Kind.MIN, count, limit);
                }
            }
            case "max" -> {
                if (count > limit) {
                    add(here, c, attach, Violation.Kind.MAX, count, limit);
                }
            }
            case "exactly" -> {
                if (count != limit) {
                    add(here, c, attach, Violation.Kind.EXACTLY, count, limit);
                }
            }
            default -> diagnostics.add("constraint type " + c.type() + ": not supported");
        }
    }

    private void add(Selection here, Constraint c, Selection attach, Violation.Kind kind, double count, double limit) {
        String message = c.message();
        if (message == null || message.isEmpty()) {
            message = defaultMessage(here, c, attach, kind, count, limit);
        }
        out.add(new Violation(attach, kind, c, count, limit, message));
    }

    /** New Recruit's wording when the data gives no message (observed, including its odd word order for min). */
    private String defaultMessage(
            Selection here, Constraint c, Selection attach, Violation.Kind kind, double count, double limit) {
        String unit = unitWord(here, c);
        String of = label(here, c);
        String n = NumberText.format(Math.abs(limit - count));
        return switch (kind) {
            case MIN -> attach.name() + " requires " + n + " " + unit + " more" + of;
            case EXACTLY ->
                count < limit
                        ? attach.name() + " requires " + n + " more " + unit + of
                        : attach.name() + " has " + n + " " + unit + " too many" + of + " (max "
                                + NumberText.format(limit) + ")";
            default ->
                attach.name() + " has " + n + " " + unit + " too many" + of + " (max " + NumberText.format(limit) + ")";
        };
    }

    private String unitWord(Selection here, Constraint c) {
        if (c.field().equals("selections") || c.field().equals("forces")) {
            return c.field();
        }
        var type = model.data().costType(here.file(), c.field());
        return type == null ? c.field() : type.name();
    }

    /** " of X": the constraint's name, else the name of the entry it counts if that is not the owner or "any". */
    private String label(Selection here, Constraint c) {
        String name = c.name();
        if (name != null && !name.isEmpty()) {
            return " of " + name;
        }
        String id = c.childId();
        if (id.isEmpty() || id.equals("any") || here.entry().id().equals(id)) {
            return "";
        }
        GameData.Resolved target = model.data().entry(here.file(), id);
        return target == null ? "" : " of " + target.entry().name();
    }

    // ---- roster

    private void costLimits() {
        Map<String, Double> totals = view.totalCosts();
        for (var e : totals.entrySet()) {
            double limit = model.costLimit(e.getKey());
            if (limit >= 0 && e.getValue() > limit) {
                String name = costName(e.getKey());
                String n = NumberText.format(e.getValue() - limit);
                out.add(new Violation(
                        null,
                        Violation.Kind.COST_LIMIT,
                        null,
                        e.getValue(),
                        limit,
                        "Roster has " + n + " " + name + " too many (max " + NumberText.format(limit) + ")"));
            }
        }
    }

    private String costName(String typeId) {
        if (!model.forces().isEmpty()) {
            var t = model.data().costType(model.forces().get(0).catalogue().file(), typeId);
            if (t != null) {
                return t.name();
            }
        }
        return typeId;
    }
}
