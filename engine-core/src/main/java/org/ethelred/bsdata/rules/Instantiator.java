/* (C) Edward Harman and contributors 2026 */
package org.ethelred.bsdata.rules;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.ethelred.bsdata.model.Catalogue;
import org.ethelred.bsdata.model.Child;
import org.ethelred.bsdata.model.Entry;
import org.ethelred.bsdata.model.GameData;
import org.ethelred.bsdata.model.Link;
import org.ethelred.bsdata.roster.ForceNode;
import org.ethelred.bsdata.roster.RosterModel;
import org.ethelred.bsdata.roster.Selection;
import org.jspecify.annotations.Nullable;

/**
 * Builds the selections New Recruit adds by itself, as observed with the {@code M6 Instantiate} and
 * {@code M5 Constraints} catalogues (spec section 6.2):
 *
 * <ul>
 *   <li><b>A new force</b> gets each visible unit at the top of its catalogue as many times (amount 1 each) as the
 *       largest {@code min} or {@code exactly} on that unit, whatever its {@code childId} and scope; percentages and
 *       limits of 0 or less add nothing; the largest {@code min} wins over a smaller {@code max}.
 *   <li><b>A new selection</b> gets, recursively, each child entry up to its own {@code min} when the constraint is on
 *       the child, counts the child itself or {@code any} (or has no {@code childId}), and has scope {@code parent},
 *       {@code self} or {@code root-entry} (a {@code force} scope adds nothing; others untested). Only {@code min}
 *       counts: an {@code exactly} on a child adds nothing. Hidden children are added too. A constraint on the unit
 *       that counts one of its children (not itself) adds nothing.
 *   <li><b>A group</b> with a {@code min} gets its default member, or its first member, that many times.
 *   <li>Links count: constraints on the target entry and on the link both apply.
 * </ul>
 *
 * {@code automatic} makes no difference here; it only stops the user going below the minimum.
 */
public final class Instantiator {
    private static final Set<String> CHILD_SCOPES = Set.of("parent", "self", "root-entry");

    private final RosterModel model;

    public Instantiator(RosterModel model) {
        this.model = model;
    }

    /** Adds the units a new force gets, each with its own instantiated children. */
    public List<Selection> populateForce(ForceNode force) {
        List<Selection> out = new ArrayList<>();
        Catalogue cat = force.catalogue();
        List<Child> roots = new ArrayList<>(cat.rootLinks());
        for (Entry e : cat.rootEntries()) {
            if (!e.group()) {
                roots.add(e);
            }
        }
        for (Child child : roots) {
            Resolved r = resolve(cat.file(), child);
            if (r == null
                    || r.entry().group()
                    || r.entry().hidden()
                    || r.link() != null && r.link().hidden()) {
                continue;
            }
            int n = (int) rootCount(r);
            for (int i = 0; i < n; i++) {
                Selection s = force.add(child, 1);
                if (s != null) {
                    populate(s);
                    out.add(s);
                }
            }
        }
        return out;
    }

    /** Adds one unit to a force and instantiates its children. */
    public @Nullable Selection addUnit(ForceNode force, Child child) {
        Selection s = force.add(child, 1);
        if (s != null) {
            populate(s);
        }
        return s;
    }

    /** Adds what a selection's entry asks for under it, and below that, recursively. */
    public void populate(Selection s) {
        fill(s, s.entry().body().children());
        var link = s.link();
        if (link != null) {
            fill(s, link.body().children());
        }
    }

    private record Resolved(Entry entry, @Nullable Link link) {}

    private @Nullable Resolved resolve(String file, Child child) {
        return switch (child) {
            case Entry e -> new Resolved(e, null);
            case Link l -> {
                GameData.Resolved t = model.data().target(file, l);
                yield t == null ? null : new Resolved(t.entry(), l);
            }
        };
    }

    private double rootCount(Resolved r) {
        double most = 0;
        for (Constraint c : constraints(r)) {
            if ((c.type().equals("min") || c.type().equals("exactly")) && !c.percentValue()) {
                most = Math.max(most, c.value());
            }
        }
        return most;
    }

    private static List<Constraint> constraints(Resolved r) {
        List<Constraint> out = new ArrayList<>(Constraint.of(r.entry().body()));
        if (r.link() != null) {
            out.addAll(Constraint.of(r.link().body()));
        }
        return out;
    }

    /** The largest {@code min} that asks for this entry (or group) to be present under its parent. */
    private static double childMin(Resolved r) {
        double most = 0;
        for (Constraint c : constraints(r)) {
            String id = c.childId();
            boolean own =
                    id.isEmpty() || id.equals("any") || id.equals(r.entry().id());
            if (c.type().equals("min") && !c.percentValue() && own && CHILD_SCOPES.contains(c.scope())) {
                most = Math.max(most, c.value());
            }
        }
        return most;
    }

    private void fill(Selection s, List<Child> children) {
        for (Child child : children) {
            Resolved r = resolve(s.file(), child);
            if (r == null) {
                continue;
            }
            double min = childMin(r);
            if (r.entry().group()) {
                if (min > 0 && !anySelected(s, r.entry())) {
                    Child pick = pick(s, r.entry());
                    if (pick != null) {
                        Selection c = s.add(pick, min);
                        if (c != null) {
                            populate(c);
                        }
                    }
                }
                fill(s, r.entry().body().children());
                if (r.link() != null) {
                    fill(s, r.link().body().children());
                }
            } else if (min > 0 && !selected(s, r.entry())) {
                Selection c = s.add(child, min);
                if (c != null) {
                    populate(c);
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

    private static boolean anySelected(Selection host, Entry group) {
        for (Selection c : host.children()) {
            if (c.groupIds().contains(group.id())) {
                return true;
            }
        }
        return false;
    }

    /** The group's default member if it names one, else its first member (looking into nested groups). */
    private @Nullable Child pick(Selection host, Entry group) {
        String def = group.defaultSelectionEntryId();
        Child first = null;
        for (Child c : group.body().children()) {
            Resolved r = resolve(host.file(), c);
            if (r == null) {
                continue;
            }
            if (r.entry().group()) {
                Child nested = pick(host, r.entry());
                if (nested != null && first == null) {
                    first = nested;
                }
                continue;
            }
            if (def != null && (r.entry().id().equals(def) || c instanceof Link l && l.id().equals(def))) {
                return c;
            }
            if (first == null) {
                first = c;
            }
        }
        return first;
    }
}
