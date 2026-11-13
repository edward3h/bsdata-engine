/* (C) Edward Harman and contributors 2026 */
package org.ethelred.bsdata.rules;

import java.util.ArrayList;
import java.util.List;
import org.ethelred.bsdata.DataNode;

/** All the conditions of one owner (a modifier, a modifier group, a constraint): direct, grouped and local. */
public record Conditions(List<Condition> conditions, List<ConditionGroup> groups, List<LocalGroup> localGroups) {
    public static final Conditions NONE = new Conditions(List.of(), List.of(), List.of());

    /** Reads the {@code conditions}, {@code conditionGroups} and {@code localConditionGroups} of an object. */
    public static Conditions of(DataNode owner) {
        return of(Json.of(owner));
    }

    static Conditions of(Json o) {
        List<Condition> conditions = conditions(o);
        List<ConditionGroup> groups = groups(o);
        List<LocalGroup> locals = locals(o);
        if (conditions.isEmpty() && groups.isEmpty() && locals.isEmpty()) {
            return NONE;
        }
        return new Conditions(conditions, groups, locals);
    }

    public boolean isEmpty() {
        return conditions.isEmpty() && groups.isEmpty() && localGroups.isEmpty();
    }

    static List<Condition> conditions(Json o) {
        List<Condition> out = new ArrayList<>();
        for (Json c : o.objs("conditions")) {
            out.add(new Condition(
                    c.str("type", "atLeast"),
                    c.str("field", "selections"),
                    c.str("scope", "self"),
                    c.str("childId", "any"),
                    c.num("value", 0),
                    c.bool("includeChildSelections"),
                    c.bool("includeChildForces"),
                    c.bool("percentValue"),
                    c.bool("shared")));
        }
        return out;
    }

    static List<ConditionGroup> groups(Json o) {
        List<ConditionGroup> out = new ArrayList<>();
        for (Json g : o.objs("conditionGroups")) {
            out.add(new ConditionGroup(
                    g.str("type", "and"), g.optNum("min"), g.optNum("max"), conditions(g), groups(g), locals(g)));
        }
        return out;
    }

    static List<LocalGroup> locals(Json o) {
        List<LocalGroup> out = new ArrayList<>();
        for (Json g : o.objs("localConditionGroups")) {
            out.add(new LocalGroup(
                    g.str("type", "atLeast"),
                    g.num("value", 0),
                    g.str("scope", "parent"),
                    g.str("field", "selections"),
                    g.bool("includeChildSelections"),
                    g.bool("includeChildForces"),
                    g.num("repeats", 1),
                    conditions(g),
                    groups(g)));
        }
        return out;
    }

    /** The query a condition counts. */
    public static Query query(Condition c) {
        return new Query(
                c.field(),
                c.scope(),
                c.childId(),
                c.includeChildSelections(),
                c.includeChildForces(),
                c.percentValue());
    }

    /** The queries of a modifier's {@code repeats}. */
    public static List<Repeat> repeats(DataNode owner) {
        List<Repeat> out = new ArrayList<>();
        for (Json r : Json.of(owner).objs("repeats")) {
            out.add(new Repeat(
                    new Query(
                            r.str("field", "selections"),
                            r.str("scope", "self"),
                            r.str("childId", "any"),
                            r.bool("includeChildSelections"),
                            r.bool("includeChildForces"),
                            r.bool("percentValue")),
                    r.num("value", 1),
                    r.num("repeats", 1),
                    r.bool("roundUp")));
        }
        return out;
    }
}
