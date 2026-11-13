/* (C) Edward Harman and contributors 2026 */
package org.ethelred.bsdata.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import org.ethelred.bsdata.TestData;
import org.ethelred.bsdata.roster.Selection;
import org.ethelred.bsdata.rules.Evaluator.Result;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * The scope table (spec 5.9) and counting, on the M1 Costs tree:
 * {@code roster > force > Units > [Hero, Shared unit, Squad > [Banner, Trooper > Gun, Opt A]]}.
 * Rows of the table that no New Recruit experiment has confirmed yet are marked "from the notes".
 */
class ScopesTest {
    private final Fixture f = new Fixture("M1 Costs");
    private final Selection hero = f.unit("M1 Hero");
    private final Selection shared = f.unit("M1 Shared");
    private final Selection squad = f.unit("M1 Squad");
    private final Selection banner = f.add(squad, "Banner", 1);
    private final Selection trooper = f.add(squad, "Trooper", 3);
    private final Selection gun = f.add(trooper, "Gun", 2);
    private final Selection optA = f.add(squad, "Opt A", 2);
    private final Evaluator ev = new Evaluator(f.model);

    /** A count that may be null (the scope resolved to nothing). */
    private @Nullable Double maybe(Selection here, String field, String scope, String childId, boolean deep) {
        return ev.count(here, new Query(field, scope, childId, deep, false, false));
    }

    /** A count that must exist. */
    private double count(Selection here, String field, String scope, String childId, boolean deep) {
        return TestData.required(maybe(here, field, scope, childId, deep));
    }

    private double sel(Selection here, String scope, String childId, boolean deep) {
        return count(here, "selections", scope, childId, deep);
    }

    @Test
    void amountsAreRelativeToTheScopeNode() {
        // Gun is 2 per Trooper, and there are 3 Troopers; from the Trooper it counts as 2 (observed in NR)
        assertEquals(2.0, sel(trooper, "self", "any", false));
        // from the Squad the Guns are 2 x 3 = 6
        assertEquals(6.0, sel(squad, "self", gun.entry().id(), true));
        assertEquals(3.0, sel(squad, "self", "model", false));
    }

    @Test
    void selfCountsWhatIsBelowAndSelfSuffixAddsTheNodeItself() {
        assertEquals(0.0, sel(gun, "self", "any", true));
        // -self does not apply to self: "self-self" is not a scope, so it resolves to nothing (observed)
        assertNull(maybe(gun, "selections", "self-self", "any", true));
        assertEquals(
                2.0, sel(gun, "upgrade-self", "any", true)); // the Gun itself, at its own amount (not its total, 6)
        assertEquals(1.0 + 3 + 6 + 2, sel(squad, "self", "any", true), "banner, troopers, guns and Opt A");
    }

    @Test
    void parentIsTheNearestEntryOrTheRoster() {
        assertEquals(2.0, sel(gun, "parent", "any", false)); // the Trooper's children, relative to the Trooper
        assertEquals(3.0, sel(trooper, "parent", "model", false)); // from the Squad
        // a top-level unit's parent skips the category and force and is the roster: it sees the forces below it
        assertEquals(15.0, sel(squad, "parent", "any", true)); // everything in the roster
        assertEquals(3.0, sel(squad, "parent", "any", false)); // the roster's direct children are the three units
    }

    @Test
    void rootEntryIsTheUnitAsAddedToTheForce() {
        assertEquals(1.0 + 3 + 6 + 2, sel(gun, "root-entry", "any", true));
        assertEquals(1.0 + 1 + 3 + 6 + 2, sel(gun, "root-entry-self", "any", true));
    }

    @Test
    void forceRosterAndPrimaryCategoryReachTheWholeTree() {
        double all = 1 + 1 + 1 + 1 + 3 + 6 + 2;
        assertEquals(all, sel(gun, "force", "any", true));
        assertEquals(all, sel(gun, "roster", "any", true));
        assertEquals(all, sel(gun, "primary-category", "any", true));
        assertEquals(3.0, sel(gun, "primary-category", "any", false)); // the three units
        assertEquals(all, sel(gun, "primary-catalogue", "any", true));
    }

    @Test
    void typeScopesFindTheNearestAncestorOfThatType() {
        assertEquals(2.0, sel(gun, "model", "any", false)); // Trooper's children: the Gun, 2 per Trooper
        assertEquals(1.0 + 3 + 6 + 2, sel(gun, "unit", "any", true)); // Squad
        assertEquals(2.0, sel(gun, "model-or-unit", "any", false)); // Trooper, the nearer
        assertEquals(2.0, sel(gun, "non-upgrade-entry", "any", false)); // Trooper: Gun itself is an upgrade
        // with no entry above that is not an upgrade, it falls back to the roster (observed)
        assertEquals(3.0, sel(squad, "non-upgrade-entry", "any", false));
        assertNull(maybe(optA, "selections", "upgrade", "any", false)); // no upgrade above it: resolves to nothing
        // model-self from Gun is the Trooper, and -self counts the Trooper itself (3) as well as the Gun below it (2)
        assertEquals(5.0, sel(gun, "model-self", "any", false));
    }

    @Test
    void anIdScopeFindsTheNearestAncestorThatHasIt() {
        String squadId = squad.entry().id();
        assertEquals(1.0 + 3 + 6 + 2, sel(gun, squadId, "any", true));
        assertNull(maybe(gun, "selections", "no-such-id", "any", true));
    }

    @Test
    void ancestorCountingIsNotModelledButInstanceOfUsesTheChain() {
        assertNull(maybe(gun, "selections", "ancestor", "unit", true));
        Condition squadIsAbove = new Condition(
                "instanceOf", "selections", "ancestor", squad.entry().id(), 1, false, false, false, true);
        assertEquals(Result.TRUE, ev.eval(gun, squadIsAbove));
        Condition heroIsAbove = new Condition(
                "instanceOf", "selections", "ancestor", hero.entry().id(), 1, false, false, false, true);
        assertEquals(Result.FALSE, ev.eval(gun, heroIsAbove));
    }

    @Test
    void theGroupScopeCountsTheMembersOfTheGroup() {
        // Opt A (2) is the only member selected from the Options group
        assertEquals(2.0, sel(optA, "group", "any", false));
        assertNull(maybe(gun, "selections", "group", "any", false)); // the Gun is in no group
    }

    @Test
    void selfWithTheScopeNodesOwnIdCountsLikeAny() {
        // observed in NR: scope self with the node's own entry id as childId counts everything below
        assertEquals(2.0, sel(trooper, "self", trooper.entry().id(), false));
    }

    @Test
    void childIdMatchesTypeEntryGroupAndCategory() {
        String gunId = gun.entry().id();
        assertEquals(6.0, sel(squad, "self", gunId, true));
        assertEquals(3.0, sel(squad, "self", "model", true));
        // Opt A sits in the Options group
        String optionsId = Fixture.entry(squad.entry(), "Options").id();
        assertEquals(2.0, sel(squad, "self", optionsId, true));
        // the three units are linked to the Units category
        String unitsId = f.force.categories().get(0).categoryId();
        assertEquals(3.0, sel(squad, "roster", unitsId, true));
    }

    @Test
    void percentValueIsARatioToTheCountOfEverything() {
        Query q = new Query("selections", "root-entry", trooper.entry().id(), true, false, true);
        assertEquals(3.0 / 12, TestData.required(ev.count(gun, q)));
    }

    @Test
    void costFieldsSumTheCostsOfTheSelectionsCounted() {
        String pts = f.costTypeId("pts");
        // no modifiers yet, so these are base costs: Banner 3, Trooper 3 x 5, Gun 6 x 2, Opt A 2 x 1. Observed in NR: a
        // cost query does not include the scope node's own cost, even with -self (the Squad's own 10 is left out)
        assertEquals(3.0 + 15 + 12 + 2, count(gun, pts, "root-entry-self", "any", true));
        // from the roster: Hero 30 pts is not in this test data (it costs 30, replaced by its link's 5), Shared unit 3
        // (link), Squad 10 and everything in it
        assertEquals(5.0 + 3 + 10 + 3 + 15 + 12 + 2, count(gun, pts, "roster", "any", true));
    }

    @Test
    void forcesAndLimits() {
        assertEquals(1.0, count(gun, "forces", "roster", "any", true));
        String pts = f.costTypeId("pts");
        assertEquals(-1.0, count(gun, "limit::" + pts, "roster", "any", true));
        f.model.setCostLimit(pts, 500);
        assertEquals(500.0, count(gun, "limit::" + pts, "roster", "any", true));
        assertNull(maybe(gun, "limit::" + pts, "self", "any", true));
        assertEquals(List.of("limit queries are only valid at roster scope"), ev.diagnostics());
    }

    @Test
    void unsupportedScopesAndFieldsAreReportedNotGuessed() {
        assertNull(maybe(gun, "associations", "self", "any", true));
        assertNull(maybe(gun, "selections", "ancestor", "any", true));
        assertEquals(
                List.of(
                        "field associations: not supported until M7",
                        "scope ancestor: counting is not modelled (New Recruit's counts depend on the host in ways not"
                                + " yet understood); instanceOf works"),
                ev.diagnostics());
    }

    @Test
    void repeatsCountThenDivideAndRound() {
        Query q = new Query("selections", "root-entry", "model", true, false, false);
        assertEquals(1.0, TestData.required(ev.times(gun, new Repeat(q, 2, 1, false)))); // 3 troopers / 2, down
        assertEquals(2.0, TestData.required(ev.times(gun, new Repeat(q, 2, 1, true)))); // rounded up
        assertEquals(6.0, TestData.required(ev.times(gun, new Repeat(q, 1, 2, false)))); // 3 / 1 x 2
        assertNull(ev.times(
                gun, new Repeat(new Query("selections", "no-such-id", "any", true, false, false), 1, 1, false)));
    }

    @Test
    void instanceOfAsksWhatTheScopeNodeIs() {
        Condition is =
                new Condition("instanceOf", "selections", "self", gun.entry().id(), 1, false, false, false, true);
        Condition isNot =
                new Condition("notInstanceOf", "selections", "self", gun.entry().id(), 1, false, false, false, true);
        assertEquals(Result.TRUE, ev.eval(gun, is));
        assertEquals(Result.FALSE, ev.eval(gun, isNot));
        assertEquals(Result.FALSE, ev.eval(trooper, is));
        Condition parentIsTrooper = new Condition(
                "instanceOf", "selections", "parent", trooper.entry().id(), 1, false, false, false, true);
        assertEquals(Result.TRUE, ev.eval(gun, parentIsTrooper));
    }

    @Test
    void atLeastAtSelfScopeWithACountOfZeroIsFalseEvenForZero() {
        // kept from BattleScribe (spec 5.12); at another scope it is true
        Condition self = new Condition("atLeast", "selections", "self", "no-match", 0, false, false, false, true);
        Condition parent = new Condition("atLeast", "selections", "parent", "no-match", 0, false, false, false, true);
        assertEquals(Result.FALSE, ev.eval(trooper, self));
        assertEquals(Result.TRUE, ev.eval(trooper, parent));
    }

    @Test
    void aScopeThatResolvesToNothingCountsAsZero() {
        // observed in NR (condition battery): not disabled; the comparison is made with 0
        Condition atLeast1 = new Condition("atLeast", "selections", "no-such-id", "any", 1, true, false, false, true);
        Condition atMost0 = new Condition("atMost", "selections", "no-such-id", "any", 0, true, false, false, true);
        Condition atLeast0 = new Condition("atLeast", "selections", "no-such-id", "any", 0, true, false, false, true);
        assertEquals(Result.FALSE, ev.eval(gun, atLeast1));
        assertEquals(Result.TRUE, ev.eval(gun, atMost0));
        assertEquals(Result.TRUE, ev.eval(gun, atLeast0));
    }

    @Test
    void theTreeHasCategoriesAndAStableOrder() {
        assertEquals("Units", f.force.categories().get(0).name());
        assertEquals(f.force.categories().get(0), hero.up());
        assertEquals(f.force, f.force.categories().get(0).up());
        assertEquals(f.model, f.force.up());
        assertEquals(squad, banner.up());
        assertEquals(3, f.force.selections().size());
        assertEquals(
                List.of(0, 1, 2, 3, 4, 5, 6),
                f.model.selections().stream().map(f.model::order).toList());
        assertEquals("Opt A", f.model.selections().get(6).name());
    }
}
