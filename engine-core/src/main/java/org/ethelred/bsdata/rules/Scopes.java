/* (C) Edward Harman and contributors 2026 */
package org.ethelred.bsdata.rules;

import java.util.ArrayList;
import java.util.List;
import org.ethelred.bsdata.roster.CategoryNode;
import org.ethelred.bsdata.roster.ForceNode;
import org.ethelred.bsdata.roster.RosterModel;
import org.ethelred.bsdata.roster.Selection;
import org.ethelred.bsdata.roster.TreeNode;
import org.jspecify.annotations.Nullable;

/**
 * Resolves a scope word to a node of the roster tree, relative to "here" (the node the condition sits on), following
 * the table in {@code docs/bsdata-extensions-spec.md}, section 5.9, which comes from the New Recruit editor's notes.
 */
final class Scopes {
    /** How the nodes of a {@link Found} are used. */
    enum Kind {
        /** One node; what is counted is what lies below it. */
        NODE,
        /** The chain from here up ({@code ancestor}); only {@code instanceOf} uses it. */
        CHAIN,
        /** The members of a group: the entries in the same group as here, below one parent. */
        MEMBERS
    }

    /**
     * @param nodes the scope node, the chain for {@code ancestor}, or the members of a group
     * @param includeSelf whether the node itself is a candidate for counting ({@code -self} scopes)
     */
    record Found(List<TreeNode> nodes, boolean includeSelf, Kind kind) {
        boolean chain() {
            return kind == Kind.CHAIN;
        }
    }

    private Scopes() {}

    /** @param unsupported receives the scope word if it cannot be resolved because the tree lacks the node it needs */
    static @Nullable Found resolve(TreeNode here, String scope, RosterModel model, List<String> unsupported) {
        boolean suffix = scope.endsWith("-self");
        String stripped = suffix ? scope.substring(0, scope.length() - 5) : scope;
        // observed: "self-self" is not a scope (New Recruit counted nothing), so it is looked up as an id
        boolean invalid = suffix && (stripped.equals("self") || stripped.equals("ancestor"));
        final boolean self = suffix && !invalid;
        final String base = invalid ? scope : stripped;
        switch (base) {
            case "self":
                return one(here, self);
            case "parent":
                return one(parent(here, model), self);
            case "ancestor":
                return new Found(chain(here), self, Kind.CHAIN);
            case "root-entry":
                return one(here instanceof Selection s ? s.rootEntry() : null, self);
            case "primary-category":
                return one(here instanceof Selection s ? s.rootEntry().category() : null, self);
            case "force":
            case "primary-catalogue":
                return one(force(here), self);
            case "roster":
                return one(model, self);
            case "group":
                return members(here);
            case "unit":
            case "model":
            case "upgrade":
                return one(nearest(here, self, s -> s.type().equals(base)), self);
            case "model-or-unit":
                return one(
                        nearest(
                                here,
                                self,
                                s -> s.type().equals("model") || s.type().equals("unit")),
                        self);
            case "non-upgrade-entry": {
                // the roster is not an upgrade, so it is where this ends if no entry above qualifies (observed)
                TreeNode found = nearest(here, self, s -> !s.type().equals("upgrade"));
                return one(found == null ? model : found, self);
            }
            case "link":
                return one(nearest(here, self, s -> s.link() != null), self);
            default:
                return byId(here, base, self);
        }
    }

    private static @Nullable Found one(@Nullable TreeNode node, boolean self) {
        return node == null ? null : new Found(List.of(node), self, Kind.NODE);
    }

    /** The nearest ancestor that is an entry (a selection) or the roster; categories and forces are skipped. */
    private static TreeNode parent(TreeNode here, RosterModel model) {
        TreeNode up = here.up();
        while (up != null) {
            if (up instanceof Selection || up instanceof RosterModel) {
                return up;
            }
            up = up.up();
        }
        return model;
    }

    /** The entries in the same group as {@code here} (the nearest group it sits in), below the same parent. */
    private static @Nullable Found members(TreeNode here) {
        if (!(here instanceof Selection s) || s.groupIds().isEmpty()) {
            return null;
        }
        Selection parent = s.parent();
        if (parent == null) {
            return null;
        }
        String group = s.groupIds().get(0);
        List<TreeNode> out = new ArrayList<>();
        for (Selection sibling : parent.children()) {
            if (sibling.groupIds().contains(group)) {
                out.add(sibling);
            }
        }
        return new Found(out, false, Kind.MEMBERS);
    }

    private static List<TreeNode> chain(TreeNode here) {
        List<TreeNode> out = new ArrayList<>();
        for (TreeNode n = here; n != null; n = n.up()) {
            out.add(n);
        }
        return out;
    }

    private static @Nullable TreeNode force(TreeNode here) {
        for (TreeNode n = here; n != null; n = n.up()) {
            if (n instanceof ForceNode) {
                return n;
            }
        }
        return null;
    }

    private interface SelectionTest {
        boolean test(Selection s);
    }

    /** The nearest ancestor selection passing the test; here itself is considered only for {@code -self}. */
    private static @Nullable TreeNode nearest(TreeNode here, boolean includeHere, SelectionTest test) {
        TreeNode n = includeHere ? here : here.up();
        for (; n != null; n = n.up()) {
            if (n instanceof Selection s && test.test(s)) {
                return s;
            }
        }
        return null;
    }

    /**
     * Any other word is an entry or category id: here if its entry id, link id or target id matches, else the nearest
     * ancestor that does, else a top-level selection in the force with that id.
     */
    private static @Nullable Found byId(TreeNode here, String id, boolean self) {
        for (TreeNode n = here; n != null; n = n.up()) {
            if (n instanceof Selection s && matchesIds(s, id)) {
                return one(s, self);
            }
            if (n instanceof CategoryNode c && c.categoryId().equals(id)) {
                return one(c, self);
            }
        }
        TreeNode f = force(here);
        if (f instanceof ForceNode force) {
            for (Selection s : force.selections()) {
                if (matchesIds(s, id)) {
                    return one(s, self);
                }
            }
        }
        return null;
    }

    private static boolean matchesIds(Selection s, String id) {
        if (s.entry().id().equals(id)) {
            return true;
        }
        var link = s.link();
        return link != null && (link.id().equals(id) || link.targetId().equals(id));
    }
}
