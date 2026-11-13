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
import org.ethelred.bsdata.model.Parts.CategoryLink;
import org.ethelred.bsdata.model.Parts.Cost;
import org.jspecify.annotations.Nullable;

/**
 * A node of the roster tree: an entry chosen with an amount. The amount is per parent, so the total number of a
 * selection is its amount times the totals of its ancestors (a Gun amount 2 under a Trooper amount 3 is 6 guns).
 */
public final class Selection implements TreeNode {
    private final RosterModel model;
    private final String id;
    private final Entry entry;
    private final @Nullable Link link;
    private final String file;
    private final @Nullable Selection parent;
    private double amount;
    private final List<Selection> children = new ArrayList<>();
    private @Nullable ForceNode force;
    private @Nullable CategoryNode category;
    private @Nullable List<String> groupIds;
    private final List<Attachment> outgoing = new ArrayList<>();
    private final List<Attachment> incoming = new ArrayList<>();

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

    /** Attachments this selection holds as a leader. */
    public List<Attachment> outgoing() {
        return Collections.unmodifiableList(outgoing);
    }

    /** Attachments of other selections to this one. */
    public List<Attachment> incoming() {
        return Collections.unmodifiableList(incoming);
    }

    void addAttachment(Attachment a) {
        outgoing.add(a);
        a.target().incoming.add(a);
    }

    void removeAttachment(Attachment a) {
        outgoing.remove(a);
        a.target().incoming.remove(a);
    }

    void attach(Selection child) {
        children.add(child);
    }

    /** Places a top-level selection in a force, and in a category if it has one. */
    void placeIn(ForceNode force, @Nullable CategoryNode category) {
        this.force = force;
        this.category = category;
    }

    @Override
    public TreeNode up() {
        if (parent != null) {
            return parent;
        }
        CategoryNode c = category;
        if (c != null) {
            return c;
        }
        ForceNode f = force;
        return f != null ? f : model;
    }

    @Override
    public List<Selection> down() {
        return children();
    }

    /** The category node this top-level selection sits in, or null (deeper selections, or no matching category). */
    public @Nullable CategoryNode category() {
        return category;
    }

    /** The force this selection is in. */
    public @Nullable ForceNode force() {
        Selection s = this;
        while (s.parent != null) {
            s = s.parent;
        }
        return s.force;
    }

    /** The top-level selection above this one, or this one: the unit as it was added to the force. */
    public Selection rootEntry() {
        Selection s = this;
        while (s.parent != null) {
            s = s.parent;
        }
        return s;
    }

    /** The entry's type ({@code unit}, {@code model}, {@code upgrade}), or an empty string for a group. */
    public String type() {
        String t = entry.type();
        return t == null ? "" : t;
    }

    /** The id of the category this selection belongs to by its own primary category link, or null. */
    public @Nullable String primaryCategoryId() {
        if (link != null) {
            for (CategoryLink c : link.body().categoryLinks()) {
                if (c.primary()) {
                    return c.targetId();
                }
            }
        }
        for (CategoryLink c : entry.body().categoryLinks()) {
            if (c.primary()) {
                return c.targetId();
            }
        }
        return null;
    }

    /** Ids of the groups this selection's entry sits in, inside its parent's entry (nearest first). */
    public List<String> groupIds() {
        List<String> cached = groupIds;
        if (cached != null) {
            return cached;
        }
        List<String> out = new ArrayList<>();
        if (parent != null) {
            findGroups(parent.entry.body().children(), out);
            if (parent.link != null) {
                findGroups(parent.link.body().children(), out);
            }
        }
        groupIds = out;
        return out;
    }

    /** Looks for this selection's entry below {@code children}, adding the ids of groups on the way (innermost first). */
    private boolean findGroups(List<Child> children, List<String> out) {
        for (Child c : children) {
            if (c == entry) {
                return true;
            }
            if (c instanceof Entry e && findGroups(e.body().children(), out)) {
                if (e.group()) {
                    out.add(e.id());
                }
                return true;
            }
        }
        return false;
    }

    /**
     * Whether this selection counts as the entry, link, category or group with this id: its own entry id, the id of the
     * link it was reached through, any category it is linked to, or a group it sits in.
     */
    public boolean is(String id) {
        if (entry.id().equals(id) || link != null && link.id().equals(id)) {
            return true;
        }
        if (link != null && link.targetId().equals(id)) {
            return true;
        }
        for (CategoryLink c : entry.body().categoryLinks()) {
            if (c.targetId().equals(id)) {
                return true;
            }
        }
        if (link != null) {
            for (CategoryLink c : link.body().categoryLinks()) {
                if (c.targetId().equals(id)) {
                    return true;
                }
            }
        }
        return groupIds().contains(id);
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
