/* (C) Edward Harman and contributors 2026 */
package org.ethelred.bsdata.roster;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.ethelred.bsdata.model.Catalogue;
import org.ethelred.bsdata.model.Child;
import org.ethelred.bsdata.model.Parts.ForceEntry;
import org.jspecify.annotations.Nullable;

/** A force in a roster: a force entry taken from a catalogue, holding the selections made in it. */
public final class ForceNode implements TreeNode {
    private final RosterModel model;
    private final String id;
    private final ForceEntry entry;
    private final Catalogue catalogue;
    private final List<CategoryNode> categories = new ArrayList<>();
    private final List<Selection> uncategorised = new ArrayList<>();
    private final @Nullable ForceNode parent;
    private final List<ForceNode> children = new ArrayList<>();

    ForceNode(RosterModel model, String id, ForceEntry entry, Catalogue catalogue, @Nullable ForceNode parent) {
        this.model = model;
        this.parent = parent;
        this.id = id;
        this.entry = entry;
        this.catalogue = catalogue;
    }

    public String id() {
        return id;
    }

    public ForceEntry entry() {
        return entry;
    }

    /** The catalogue the force was added from; its scope decides how links resolve. */
    public Catalogue catalogue() {
        return catalogue;
    }

    public List<CategoryNode> categories() {
        return Collections.unmodifiableList(categories);
    }

    /** Adds a category node, in the order the force lists its categories. */
    public CategoryNode addCategory(String nodeId, String categoryId, String name, boolean primary) {
        CategoryNode c = new CategoryNode(this, nodeId, categoryId, name, primary);
        categories.add(c);
        return c;
    }

    /** The category node for a category entry id, or null if the force has none. */
    public @Nullable CategoryNode category(String categoryId) {
        for (CategoryNode c : categories) {
            if (c.categoryId().equals(categoryId)) {
                return c;
            }
        }
        return null;
    }

    /** All selections made directly in the force, in tree order: by category, then those in no category. */
    public List<Selection> selections() {
        List<Selection> out = new ArrayList<>();
        for (CategoryNode c : categories) {
            out.addAll(c.selections());
        }
        out.addAll(uncategorised);
        return Collections.unmodifiableList(out);
    }

    /** The force this one is a child of, or null for a force at the top of the roster. */
    public @Nullable ForceNode parent() {
        return parent;
    }

    /** The forces added directly under this one, in the order added. */
    public List<ForceNode> children() {
        return Collections.unmodifiableList(children);
    }

    void addChild(ForceNode child) {
        children.add(child);
    }

    /** This force and all below it, parents before children. */
    public List<ForceNode> withDescendants() {
        List<ForceNode> out = new ArrayList<>();
        out.add(this);
        for (ForceNode c : children) {
            out.addAll(c.withDescendants());
        }
        return out;
    }

    @Override
    public TreeNode up() {
        return parent != null ? parent : model;
    }

    @Override
    public List<? extends TreeNode> down() {
        List<TreeNode> out = new ArrayList<>(categories);
        out.addAll(uncategorised);
        out.addAll(children);
        return out;
    }

    /** Adds a unit (an entry or a link at the top of the catalogue), or returns null if a link cannot be followed. */
    public @Nullable Selection add(Child child, double amount) {
        return model.addUnder(null, catalogue.file(), child, amount, this);
    }

    void attach(Selection s, @Nullable CategoryNode category) {
        if (category == null) {
            uncategorised.add(s);
        } else {
            category.attach(s);
        }
    }

    RosterModel model() {
        return model;
    }

    /** The own costs of all selections in this force and its child forces (observed: a parent's total includes them). */
    public Map<String, Double> costs() {
        Map<String, Double> out = new LinkedHashMap<>();
        for (ForceNode f : withDescendants()) {
            for (Selection s : f.selections()) {
                s.subtreeCosts().forEach((type, v) -> out.merge(type, v, Double::sum));
            }
        }
        return out;
    }
}
