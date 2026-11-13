/* (C) Edward Harman and contributors 2026 */
package org.ethelred.bsdata.roster;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * A node of the roster tree: {@code roster > force > category > selection (> selection ...)}. Scopes and queries walk
 * this tree, so every level, not only selections, is a node.
 */
public sealed interface TreeNode permits RosterModel, ForceNode, CategoryNode, Selection {
    /** The node above this one, or null at the roster. */
    @Nullable
    TreeNode up();

    /** The nodes directly below, in tree order. */
    List<? extends TreeNode> down();
}
