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
public final class RosterModel {
    private final GameData data;
    private final String name;
    private final List<ForceNode> forces = new ArrayList<>();
    private int nextId = 1;

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

    public ForceNode addForce(String id, ForceEntry entry, Catalogue catalogue) {
        ForceNode f = new ForceNode(this, id, entry, catalogue);
        forces.add(f);
        return f;
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

    private static Selection attach(Selection s, @Nullable Selection parent, @Nullable ForceNode force) {
        if (parent != null) {
            parent.attach(s);
        } else if (force != null) {
            force.attach(s);
        }
        return s;
    }
}
