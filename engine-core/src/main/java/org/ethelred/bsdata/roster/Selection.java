/* (C) Edward Harman and contributors 2026 */
package org.ethelred.bsdata.roster;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.ethelred.bsdata.model.Child;
import org.ethelred.bsdata.model.Entry;
import org.ethelred.bsdata.model.GameData;
import org.ethelred.bsdata.model.Link;
import org.ethelred.bsdata.model.Parts.Cost;
import org.jspecify.annotations.Nullable;

/**
 * A node of the roster tree: an entry chosen with an amount. The amount is per parent, so the total number of a
 * selection is its amount times the totals of its ancestors (a Gun amount 2 under a Trooper amount 3 is 6 guns).
 */
public final class Selection {
    private final RosterModel model;
    private final String id;
    private final Entry entry;
    private final @Nullable Link link;
    private final String file;
    private final @Nullable Selection parent;
    private double amount;
    private final List<Selection> children = new ArrayList<>();

    Selection(
            RosterModel model,
            String id,
            Entry entry,
            @Nullable Link link,
            String file,
            @Nullable Selection parent,
            double amount) {
        this.model = model;
        this.id = id;
        this.entry = entry;
        this.link = link;
        this.file = file;
        this.parent = parent;
        this.amount = amount;
    }

    public String id() {
        return id;
    }

    public String name() {
        return entry.name();
    }

    public Entry entry() {
        return entry;
    }

    /** The link this selection was reached through, if any; its costs replace the entry's. */
    public @Nullable Link link() {
        return link;
    }

    /** The file where the entry is defined. */
    public String file() {
        return file;
    }

    public @Nullable Selection parent() {
        return parent;
    }

    public double amount() {
        return amount;
    }

    public void setAmount(double amount) {
        this.amount = amount;
    }

    public List<Selection> children() {
        return Collections.unmodifiableList(children);
    }

    /** The total number: this amount times those of all ancestors. */
    public double total() {
        return parent == null ? amount : amount * parent.total();
    }

    /**
     * Adds a selection under this one. An entry or group is used as it is; a link is followed to its target, and
     * the link is kept because its costs override the target's.
     *
     * @return the new selection, or null if a link's target cannot be found
     */
    public @Nullable Selection add(Child child, double childAmount) {
        return model.addUnder(this, file, child, childAmount, null);
    }

    void attach(Selection child) {
        children.add(child);
    }

    /**
     * The cost of one of this selection: the entry's costs, with the link's own costs replacing the entry's cost of
     * the same type (observed in New Recruit; cost types the link does not mention come from the entry).
     */
    public Map<String, Double> unitCosts() {
        Map<String, Double> out = new LinkedHashMap<>();
        for (Cost c : entry.body().costs()) {
            out.put(c.typeId(), c.value());
        }
        if (link != null) {
            for (Cost c : link.body().costs()) {
                out.put(c.typeId(), c.value());
            }
        }
        return out;
    }

    /** This selection's own cost, which is its unit cost times its total number, without its children's. */
    public Map<String, Double> costs() {
        double total = total();
        Map<String, Double> out = new LinkedHashMap<>();
        unitCosts().forEach((type, value) -> out.put(type, value * total));
        return out;
    }

    /** The own cost of this selection and all below it. */
    public Map<String, Double> subtreeCosts() {
        Map<String, Double> out = new LinkedHashMap<>(costs());
        for (Selection c : children) {
            c.subtreeCosts().forEach((type, v) -> out.merge(type, v, Double::sum));
        }
        return out;
    }

    GameData data() {
        return model.data();
    }
}
