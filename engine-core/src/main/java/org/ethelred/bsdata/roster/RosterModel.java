/* (C) Edward Harman and contributors 2026 */
package org.ethelred.bsdata.roster;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.ethelred.bsdata.model.Catalogue;
import org.ethelred.bsdata.model.Child;
import org.ethelred.bsdata.model.Entry;
import org.ethelred.bsdata.model.GameData;
import org.ethelred.bsdata.model.Link;
import org.ethelred.bsdata.model.Parts.ForceEntry;
import org.jspecify.annotations.Nullable;

/** The mutable roster tree: forces holding selections. M1 builds it from explicit choices and sums base costs. */
public final class RosterModel implements TreeNode {
    private final GameData data;
    private final String name;
    private final List<ForceNode> forces = new ArrayList<>();
    private int nextId = 1;
    private int modCount;
    private @Nullable Map<Selection, Integer> order;
    private final Map<String, Double> costLimits = new LinkedHashMap<>();

    public RosterModel(GameData data, String name) {
        this.data = data;
        this.name = name;
    }

    public GameData data() {
        return data;
    }

    public String name() {
        return name;
    }

    public List<ForceNode> forces() {
        return Collections.unmodifiableList(forces);
    }

    public ForceNode addForce(ForceEntry entry, Catalogue catalogue) {
        return addForce("f" + nextId++, entry, catalogue);
    }

    /** Adds a force with a category node for each category link of its force entry. */
    public ForceNode addForce(String id, ForceEntry entry, Catalogue catalogue) {
        ForceNode f = addBareForce(id, entry, catalogue);
        for (var link : entry.categoryLinks()) {
            f.addCategory("c" + nextId++, link.targetId(), link.name(), link.primary());
        }
        return f;
    }

    /** Adds a force with no category nodes; the caller adds them (for example from a roster file). */
    public ForceNode addBareForce(String id, ForceEntry entry, Catalogue catalogue) {
        ForceNode f = new ForceNode(this, id, entry, catalogue);
        forces.add(f);
        modCount++;
        return f;
    }

    @Override
    public @Nullable TreeNode up() {
        return null;
    }

    @Override
    public List<ForceNode> down() {
        return forces();
    }

    /** The limit set for a cost type id, or -1 for none. */
    public double costLimit(String typeId) {
        return costLimits.getOrDefault(typeId, -1.0);
    }

    public void setCostLimit(String typeId, double limit) {
        costLimits.put(typeId, limit);
    }

    /** Position of a selection in tree order (parents before children, by force and category); -1 if absent. */
    public int order(Selection s) {
        Map<Selection, Integer> o = order;
        if (o == null || o.size() != selections().size()) {
            o = new java.util.IdentityHashMap<>();
            int i = 0;
            for (Selection x : selections()) {
                o.put(x, i++);
            }
            order = o;
        }
        Integer i = o.get(s);
        return i == null ? -1 : i;
    }

    /** All selections, parents before children. */
    public List<Selection> selections() {
        List<Selection> out = new ArrayList<>();
        for (ForceNode f : forces) {
            for (Selection s : f.selections()) {
                collect(s, out);
            }
        }
        return out;
    }

    private static void collect(Selection s, List<Selection> out) {
        out.add(s);
        s.children().forEach(c -> collect(c, out));
    }

    /** The roster total for each cost type id: the plain sum of the selections' own costs. */
    public Map<String, Double> costs() {
        Map<String, Double> out = new LinkedHashMap<>();
        for (Selection s : selections()) {
            s.costs().forEach((type, v) -> out.merge(type, v, Double::sum));
        }
        return out;
    }

    /**
     * A selection that is not in the tree: it has a parent, so scopes resolve from it as they would for a real child,
     * but it is not among the parent's children and counts as nothing. The validator uses it to ask what a constraint or
     * modifier on an entry that is available but not selected would see.
     *
     * @return the probe, or null if a link's target cannot be found
     */
    public @Nullable Selection probe(Selection parent, Child child) {
        Entry entry;
        Link link = null;
        String entryFile = parent.file();
        switch (child) {
            case Entry e -> entry = e;
            case Link l -> {
                GameData.Resolved target = data.target(parent.file(), l);
                if (target == null) {
                    return null;
                }
                entry = target.entry();
                link = l;
                entryFile = target.file();
            }
        }
        return new Selection(this, "probe-" + entry.id(), entry, link, entryFile, parent, 0);
    }

    @Nullable
    Selection addUnder(@Nullable Selection parent, String file, Child child, double amount, @Nullable ForceNode force) {
        return addUnder("s" + nextId++, parent, file, child, amount, force);
    }

    @Nullable
    Selection addUnder(
            String id, @Nullable Selection parent, String file, Child child, double amount, @Nullable ForceNode force) {
        Entry entry;
        Link link = null;
        String entryFile = file;
        switch (child) {
            case Entry e -> entry = e;
            case Link l -> {
                GameData.Resolved target = data.target(file, l);
                if (target == null) {
                    return null;
                }
                entry = target.entry();
                link = l;
                entryFile = target.file();
            }
        }
        return attach(new Selection(this, id, entry, link, entryFile, parent, amount), parent, force);
    }

    @Nullable
    Selection addResolved(
            String id, @Nullable Selection parent, GameData.Resolved r, double amount, @Nullable ForceNode force) {
        return attach(new Selection(this, id, r.entry(), r.link(), r.file(), parent, amount), parent, force);
    }

    private Selection attach(Selection s, @Nullable Selection parent, @Nullable ForceNode force) {
        if (parent != null) {
            parent.attach(s);
        } else if (force != null) {
            String categoryId = s.primaryCategoryId();
            CategoryNode category = categoryId == null ? null : force.category(categoryId);
            s.placeIn(force, category);
            force.attach(s, category);
        }
        order = null;
        modCount++;
        return s;
    }
}
