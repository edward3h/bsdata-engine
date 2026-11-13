/* (C) Edward Harman and contributors 2026 */
package org.ethelred.bsdata.rules;

import java.util.ArrayList;
import java.util.List;
import org.ethelred.bsdata.DataNode;

/**
 * A modifier as far as M2 needs it: its conditions and repeats, with the rest kept as the parsed node. M3 will type
 * the type, field, value and the other attributes.
 */
public record ModifierDef(DataNode node, Conditions conditions, List<Repeat> repeats) {
    public static List<ModifierDef> of(List<DataNode> modifiers) {
        List<ModifierDef> out = new ArrayList<>();
        for (DataNode m : modifiers) {
            out.add(new ModifierDef(m, Conditions.of(m), Conditions.repeats(m)));
        }
        return out;
    }

    /** A modifier group: conditions and repeats that apply to every modifier in it, and its members. */
    public record Group(Conditions conditions, List<Repeat> repeats, List<ModifierDef> modifiers, List<Group> groups) {
        public static List<Group> of(List<DataNode> groups) {
            List<Group> out = new ArrayList<>();
            for (DataNode g : groups) {
                Json j = Json.of(g);
                List<DataNode> mods = new ArrayList<>();
                List<DataNode> nested = new ArrayList<>();
                if (g instanceof DataNode.Obj o) {
                    if (o.fields().get("modifiers") instanceof DataNode.Arr a) {
                        mods.addAll(a.items());
                    }
                    if (o.fields().get("modifierGroups") instanceof DataNode.Arr a) {
                        nested.addAll(a.items());
                    }
                }
                out.add(new Group(Conditions.of(j), Conditions.repeats(g), ModifierDef.of(mods), Group.of(nested)));
            }
            return out;
        }
    }
}
