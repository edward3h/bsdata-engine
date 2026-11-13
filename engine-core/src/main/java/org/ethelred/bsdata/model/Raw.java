/* (C) Edward Harman and contributors 2026 */
package org.ethelred.bsdata.model;

import java.util.List;
import org.ethelred.bsdata.DataNode;
import org.ethelred.bsdata.MinimalJsonSource;

/**
 * Parts of an object that M1 does not interpret yet (constraints, modifiers, modifier groups, associations). Each is
 * kept as the compact JSON text of its array, which is far smaller than a tree of nodes, and parsed only when asked
 * for. M2 and M3 will type them; until then nothing reads them on the hot path.
 */
public record Raw(String constraintsJson, String modifiersJson, String modifierGroupsJson, String associationsJson) {
    static final Raw EMPTY = new Raw("", "", "", "");

    public List<DataNode> constraints() {
        return parse(constraintsJson);
    }

    public List<DataNode> modifiers() {
        return parse(modifiersJson);
    }

    public List<DataNode> modifierGroups() {
        return parse(modifierGroupsJson);
    }

    public List<DataNode> associations() {
        return parse(associationsJson);
    }

    /** True if there is nothing in any of the four. */
    public boolean isEmpty() {
        return constraintsJson.isEmpty()
                && modifiersJson.isEmpty()
                && modifierGroupsJson.isEmpty()
                && associationsJson.isEmpty();
    }

    private static List<DataNode> parse(String json) {
        if (json.isEmpty()) {
            return List.of();
        }
        return ((DataNode.Arr) new MinimalJsonSource().parse(json)).items();
    }

    static Raw of(
            List<DataNode> constraints, List<DataNode> modifiers, List<DataNode> groups, List<DataNode> associations) {
        if (constraints.isEmpty() && modifiers.isEmpty() && groups.isEmpty() && associations.isEmpty()) {
            return EMPTY;
        }
        return new Raw(text(constraints), text(modifiers), text(groups), text(associations));
    }

    private static String text(List<DataNode> items) {
        return items.isEmpty() ? "" : new MinimalJsonSource().write(new DataNode.Arr(items));
    }
}
