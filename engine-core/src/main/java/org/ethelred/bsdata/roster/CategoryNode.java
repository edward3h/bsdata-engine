/* (C) Edward Harman and contributors 2026 */
package org.ethelred.bsdata.roster;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A category inside a force (for example "Units"): the level between a force and the entries added to it. Its
 * {@code categoryId} is the id of the category entry, which is what conditions and links refer to.
 */
public final class CategoryNode implements TreeNode {
    private final ForceNode force;
    private final String id;
    private final String categoryId;
    private final String name;
    private final boolean primary;
    private final List<Selection> selections = new ArrayList<>();

    CategoryNode(ForceNode force, String id, String categoryId, String name, boolean primary) {
        this.force = force;
        this.id = id;
        this.categoryId = categoryId;
        this.name = name;
        this.primary = primary;
    }

    /** The id of this node in the roster. */
    public String id() {
        return id;
    }

    /** The id of the category entry it stands for. */
    public String categoryId() {
        return categoryId;
    }

    public String name() {
        return name;
    }

    public boolean primary() {
        return primary;
    }

    public ForceNode force() {
        return force;
    }

    public List<Selection> selections() {
        return Collections.unmodifiableList(selections);
    }

    void attach(Selection s) {
        selections.add(s);
    }

    @Override
    public TreeNode up() {
        return force;
    }

    @Override
    public List<Selection> down() {
        return selections();
    }
}
