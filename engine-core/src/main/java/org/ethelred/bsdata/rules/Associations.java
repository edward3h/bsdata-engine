/* (C) Edward Harman and contributors 2026 */
package org.ethelred.bsdata.rules;

import java.util.ArrayList;
import java.util.List;
import org.ethelred.bsdata.roster.Attachment;
import org.ethelred.bsdata.roster.RosterModel;
import org.ethelred.bsdata.roster.Selection;

/** Which units a leader may attach to, and how many it has attached, for each association its entry offers. */
public final class Associations {
    private final RosterModel model;
    private final Evaluator evaluator;

    public Associations(RosterModel model, Evaluator evaluator) {
        this.model = model;
        this.evaluator = evaluator;
    }

    /** The associations offered by a selection's entry and by the link it was reached through. */
    public static List<Association> of(Selection s) {
        List<Association> out = new ArrayList<>(Association.of(s.entry().body()));
        var link = s.link();
        if (link != null) {
            out.addAll(Association.of(link.body()));
        }
        return out;
    }

    /**
     * The units offered for an association: every selection in its scope that matches {@code childId} and passes its
     * conditions, except the leader itself (observed: a leader is not offered itself, and nothing is offered a limit).
     */
    public List<Selection> offered(Selection leader, Association a) {
        List<String> unsupported = new ArrayList<>();
        Scopes.Found found = Scopes.resolve(leader, a.scope(), model, unsupported);
        List<Selection> out = new ArrayList<>();
        if (found == null) {
            return out;
        }
        for (Selection s : Evaluator.selectionsIn(found, a.includeChildSelections())) {
            if (s != leader
                    && Evaluator.matches(s, a.childId())
                    && evaluator.eval(s, a.conditions()) == Evaluator.Result.TRUE) {
                out.add(s);
            }
        }
        return out;
    }

    /** The units attached to a leader through one association. */
    public static List<Selection> attached(Selection leader, Association a) {
        List<Selection> out = new ArrayList<>();
        for (Attachment x : leader.outgoing()) {
            if (x.associationId().equals(a.id())) {
                out.add(x.target());
            }
        }
        return out;
    }

    /** Attaches if the unit is offered; returns whether it was. The maximum is not enforced (observed). */
    public boolean attach(Selection leader, Association a, Selection target) {
        if (!offered(leader, a).contains(target) || attached(leader, a).contains(target)) {
            return false;
        }
        model.attach(leader, target, a.id());
        return true;
    }
}
