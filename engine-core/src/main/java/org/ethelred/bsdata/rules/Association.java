/* (C) Edward Harman and contributors 2026 */
package org.ethelred.bsdata.rules;

import java.util.ArrayList;
import java.util.List;
import org.ethelred.bsdata.DataNode;
import org.ethelred.bsdata.model.Body;
import org.jspecify.annotations.Nullable;

/**
 * An association an entry offers, such as a leader attaching to a unit. The units it may attach to are those in
 * {@code scope} that match {@code childId} and whose own situation passes {@code conditions} (each acting as "self").
 * See {@code docs/bsdata-extensions-spec.md}, section 6.3.
 *
 * @param id the data's id, or {@code <name>-<childId>} when it has none (as New Recruit writes it in a roster)
 * @param label the role the leader plays, such as {@code Leader}; constraints refer to it
 * @param min fewer attached units than this is a problem
 * @param max more than this is a problem; the interface does not stop it (observed)
 */
public record Association(
        String id,
        String name,
        String action,
        String childId,
        String label,
        double min,
        double max,
        String scope,
        boolean includeChildSelections,
        Conditions conditions) {

    /** The associations in a body (an entry or a link), or none. */
    public static List<Association> of(Body body) {
        return body.raw().isEmpty() ? List.of() : of(body.raw().associations());
    }

    public static List<Association> of(List<DataNode> associations) {
        List<Association> out = new ArrayList<>();
        for (DataNode node : associations) {
            Json j = Json.of(node);
            String name = j.str("name", "");
            String childId = j.str("childId", "any");
            @Nullable String id = j.optStr("id");
            out.add(new Association(
                    id == null || id.isEmpty() ? name + "-" + childId : id,
                    name,
                    j.str("action", "group"),
                    childId,
                    j.str("label", ""),
                    j.num("min", 0),
                    j.num("max", -1),
                    j.str("scope", "force"),
                    j.bool("includeChildSelections"),
                    Conditions.of(node)));
        }
        return out;
    }
}
