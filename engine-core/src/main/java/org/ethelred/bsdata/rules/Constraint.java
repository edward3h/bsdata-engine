/* (C) Edward Harman and contributors 2026 */
package org.ethelred.bsdata.rules;

import java.util.ArrayList;
import java.util.List;
import org.ethelred.bsdata.DataNode;
import org.jspecify.annotations.Nullable;

/**
 * A constraint on an entry, a group or a link: count something (the same query a condition uses), then require the
 * count to be at least ({@code min}), at most ({@code max}) or exactly ({@code exactly}) {@code value}. See
 * {@code docs/bsdata-extensions-spec.md}, section 6.2.
 *
 * @param childId what to count; empty when the data gives none, which counts the owner's own entry (observed)
 * @param value the limit; {@code -1} means no limit unless {@code negative} is set
 * @param percentValue whether {@code value} is a percentage of the count with {@code childId} {@code any}
 * @param shared whether the constraint is shared with the entry's other uses; {@code false} is not modelled yet
 * @param automatic whether the roster builder keeps the constraint satisfied itself (a builder matter, not validation)
 * @param traverseAssociationGroup whether to count across an attached group (M7)
 * @param message the text shown when the constraint fails, if the data gives one
 * @param name the constraint's name, used in the default message when there is no {@code message}
 */
public record Constraint(
        String id,
        String type,
        double value,
        String field,
        String scope,
        String childId,
        boolean includeChildSelections,
        boolean includeChildForces,
        boolean percentValue,
        boolean shared,
        boolean negative,
        boolean automatic,
        boolean traverseAssociationGroup,
        @Nullable String message,
        @Nullable String name) {

    public static List<Constraint> of(List<DataNode> constraints) {
        List<Constraint> out = new ArrayList<>();
        for (DataNode node : constraints) {
            Json j = Json.of(node);
            out.add(new Constraint(
                    j.str("id", ""),
                    j.str("type", "max"),
                    j.num("value", 0),
                    j.str("field", "selections"),
                    j.str("scope", "self"),
                    j.str("childId", ""),
                    j.bool("includeChildSelections"),
                    j.bool("includeChildForces"),
                    j.bool("percentValue"),
                    j.bool("shared", true),
                    j.bool("negative"),
                    j.bool("automatic"),
                    j.bool("traverseAssociationGroup"),
                    j.optStr("message"),
                    j.optStr("name")));
        }
        return out;
    }

    /** The query this constraint counts, for an owner whose entry id is {@code ownerEntryId}. */
    public Query query(String ownerEntryId) {
        return new Query(
                field,
                scope,
                childId.isEmpty() ? ownerEntryId : childId,
                includeChildSelections,
                includeChildForces,
                percentValue);
    }
}
