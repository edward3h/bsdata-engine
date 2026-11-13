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
    /**
     * @param nodes the scope node, or for {@code ancestor} the whole chain from here up
     * @param includeSelf whether the node itself is a candidate for counting ({@code -self} scopes)
     * @param chain true for {@code ancestor}: the nodes themselves are the candidates, not what is below them
     */
    record Found(List<TreeNode> nodes, boolean includeSelf, boolean chain) {}

    private Scopes() {}

    /** @param unsupported receives the scope word if it cannot be resolved because the tree lacks the node it needs */
    static @Nullable Found resolve(TreeNode here, String scope, RosterModel model, List<String> unsupported) {
        boolean self = scope.endsWith("-self");
        String base = self ? scope.substring(0, scope.length() - 5) : scope;
        switch (base) {
            case "self":
                return one(here, self);
            case "parent":
                return one(parent(here, model), self);
            case "ancestor":
                return new Found(chain(here), self, true);
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
                unsupported.add("scope group: the roster tree has no group nodes yet");
                return null;
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
            case "non-upgrade-entry":
                return one(nearest(here, self, s -> !s.type().equals("upgrade")), self);
            case "link":
                return one(nearest(here, self, s -> s.link() != null), self);
            default:
                return byId(here, base, self);
        }
    }

    private static @Nullable Found one(@Nullable TreeNode node, boolean self) {
        return node == null ? null : new Found(List.of(node), self, false);
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
