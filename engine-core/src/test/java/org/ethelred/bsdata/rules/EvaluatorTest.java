/* (C) Edward Harman and contributors 2026 */
package org.ethelred.bsdata.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.ethelred.bsdata.model.Entry;
import org.ethelred.bsdata.roster.Selection;
import org.ethelred.bsdata.rules.Evaluator.Result;
import org.junit.jupiter.api.Test;

/** Conditions checked against what was observed in New Recruit on the synthetic Extension Tests data. */
class EvaluatorTest {
    private static Conditions profileConditions(Entry owner, String profileName) {
        var profile = owner.body().profiles().stream()
                .filter(p -> p.name().equals(profileName))
                .findFirst()
                .orElseThrow();
        return ModifierDef.of(profile.raw().modifiers()).get(0).conditions();
    }

    private static Result t(boolean b) {
        return b ? Result.TRUE : Result.FALSE;
    }

    // ---- T16: count groups (nr-extension-tests/RESULTS.md)

    @Test
    void countGroupsMatchTheObservedTable() {
        // rows: how many of C1..C4 are selected; columns: T16a (min 3), T16b (min 3, max 3), T16c (max 1, no min)
        boolean[][] expected = {
            {false, false, false},
            {false, false, true},
            {false, false, false},
            {true, true, false},
            {true, false, false},
        };
        String[] names = {"T16a", "T16b", "T16c"};
        for (int k = 0; k <= 4; k++) {
            Fixture f = new Fixture("Extension Tests Catalogue");
            Selection unit = f.unit("UNIT A");
            for (int c = 1; c <= k; c++) {
                f.add(unit, "C" + c + ":", 1);
            }
            Evaluator ev = new Evaluator(f.model);
            for (int col = 0; col < 3; col++) {
                assertEquals(
                        t(expected[k][col]),
                        ev.eval(unit, profileConditions(unit.entry(), names[col])),
                        k + " of C1..C4 selected, " + names[col]);
            }
        }
    }

    // ---- APPLY gating (every T01 to T19 modifier)

    @Test
    void aConditionOnASelectedChildIsTrueOnlyWhenItIsSelected() {
        Fixture f = new Fixture("Extension Tests Catalogue");
        Selection unit = f.unit("UNIT A");
        Evaluator ev = new Evaluator(f.model);
        Conditions gate = profileConditions(unit.entry(), "T01a");
        assertEquals(Result.FALSE, ev.eval(unit, gate));
        f.add(unit, "APPLY", 1);
        assertEquals(Result.TRUE, ev.eval(unit, gate));
    }

    // ---- F13: the ordinary form of before reads as "exists"

    @Test
    void anOrdinaryBeforeIsTrueForTheNodeItselfAndLaterNodes() {
        Fixture f = new Fixture("Extension Tests Catalogue");
        Selection unit = f.unit("UNIT A");
        Selection m1 = f.add(unit, "Model One", 1);
        Selection m2 = f.add(unit, "Model Two", 1);
        Evaluator ev = new Evaluator(f.model);
        for (Selection model : List.of(m1, m2)) {
            Entry e = model.entry();
            var mods = ModifierDef.of(e.body().profiles().get(0).raw().modifiers());
            // all four combinations fired when both models were present
            assertEquals(Result.TRUE, ev.eval(model, mods.get(0).conditions()), model.name());
            assertEquals(Result.TRUE, ev.eval(model, mods.get(1).conditions()), model.name());
        }
    }

    @Test
    void anOrdinaryBeforeIsFalseWhenTheCountedNodeIsAbsent() {
        Fixture f = new Fixture("Extension Tests Catalogue");
        Selection unit = f.unit("UNIT A");
        Selection m1 = f.add(unit, "Model One", 1);
        Evaluator ev = new Evaluator(f.model);
        var mods = ModifierDef.of(m1.entry().body().profiles().get(0).raw().modifiers());
        // M1's modifiers are [before Model Two, before Model One]; only Model One is present
        assertEquals(Result.FALSE, ev.eval(m1, mods.get(0).conditions()));
        assertEquals(Result.TRUE, ev.eval(m1, mods.get(1).conditions()));
    }

    // ---- F14: local condition groups (G1 to G3)

    private Result unitCondition(Fixture f, Evaluator ev, Selection copy) {
        List<ModifierDef> mods = Fixture.modifiers(copy.entry());
        return ev.eval(copy, mods.get(0).conditions());
    }

    @Test
    void g1ChargesOnlyTheThirdAndLaterCopies() {
        Fixture f = new Fixture("Extension Tests Catalogue");
        Evaluator ev = new Evaluator(f.model);
        // observed costs 100, 100, 110, 110: the surcharge applies when at least 2 earlier copies exist
        List<Result> seen = new java.util.ArrayList<>();
        for (int i = 0; i < 4; i++) {
            f.unit("G1 ");
        }
        for (Selection copy : f.force.selections()) {
            seen.add(unitCondition(f, ev, copy));
        }
        assertEquals(List.of(Result.FALSE, Result.FALSE, Result.TRUE, Result.TRUE), seen);
    }

    @Test
    void g2ChargesAllCopiesOnceThereAreTwo() {
        Fixture f = new Fixture("Extension Tests Catalogue");
        Evaluator ev = new Evaluator(f.model);
        f.unit("G2 ");
        assertEquals(Result.FALSE, unitCondition(f, ev, f.force.selections().get(0)));
        f.unit("G2 ");
        f.unit("G2 ");
        for (Selection copy : f.force.selections()) {
            assertEquals(Result.TRUE, unitCondition(f, ev, copy)); // observed 110, 110, 110
        }
    }

    @Test
    void g3ChargesWhileThereAreFewerThanTwoCopies() {
        Fixture f = new Fixture("Extension Tests Catalogue");
        Evaluator ev = new Evaluator(f.model);
        f.unit("G3 ");
        assertEquals(Result.TRUE, unitCondition(f, ev, f.force.selections().get(0)));
        f.unit("G3 ");
        for (Selection copy : f.force.selections()) {
            assertEquals(Result.FALSE, unitCondition(f, ev, copy)); // observed 100, 100
        }
    }

    @Test
    void theEvaluatorReportsNothingUnsupportedForTheObservedCases() {
        Fixture f = new Fixture("Extension Tests Catalogue");
        Evaluator ev = new Evaluator(f.model);
        Selection unit = f.unit("UNIT A");
        ev.eval(unit, profileConditions(unit.entry(), "T16a"));
        assertEquals(List.of(), ev.diagnostics());
    }

    @Test
    void anUnsetMinAndAnInclusiveMaxOnACountGroup() {
        // the group types are evaluated on constructed members, independent of any roster
        Fixture f = new Fixture("Extension Tests Catalogue");
        Selection unit = f.unit("UNIT A");
        Evaluator ev = new Evaluator(f.model);
        Condition yes = new Condition("always", "selections", "self", "any", 0, false, false, false, true);
        Condition no = new Condition("never", "selections", "self", "any", 0, false, false, false, true);
        ConditionGroup twoOfThree = new ConditionGroup("count", 2.0, 2.0, List.of(yes, yes, no), List.of(), List.of());
        assertEquals(Result.TRUE, ev.eval(unit, twoOfThree));
        ConditionGroup max1 = new ConditionGroup("count", null, 1.0, List.of(yes, yes, no), List.of(), List.of());
        assertEquals(Result.FALSE, ev.eval(unit, max1));
        ConditionGroup not = new ConditionGroup("not", null, null, List.of(no, no), List.of(), List.of());
        assertEquals(Result.TRUE, ev.eval(unit, not));
        ConditionGroup or = new ConditionGroup("or", null, null, List.of(no, yes), List.of(), List.of());
        assertEquals(Result.TRUE, ev.eval(unit, or));
        ConditionGroup and = new ConditionGroup("and", null, null, List.of(no, yes), List.of(), List.of());
        assertEquals(Result.FALSE, ev.eval(unit, and));
        ConditionGroup unsupported = new ConditionGroup("greater", null, null, List.of(yes), List.of(), List.of());
        assertEquals(Result.DISABLED, ev.eval(unit, unsupported));
        assertEquals(List.of("condition group type greater: not supported"), ev.diagnostics());
    }
}
