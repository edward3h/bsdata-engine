/* (C) Edward Harman and contributors 2026 */
package org.ethelred.bsdata.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.ethelred.bsdata.TestData;
import org.ethelred.bsdata.model.Parts.Profile;
import org.ethelred.bsdata.roster.Selection;
import org.junit.jupiter.api.Test;

/** Compares the engine with what New Recruit did for the synthetic {@code M7 Associations} catalogue. */
class AssociationTest {
    private static final String KEYWORDS = "a2d2-6932-5906-9eaf";

    private final Fixture fx = new Fixture("M7 Associations");

    private boolean attach(Selection leader, Selection target) {
        return new Associations(fx.model, new Evaluator(fx.model))
                .attach(leader, Associations.of(leader).get(0), target);
    }

    /** The keywords of a selection's first profile (a unit's own weapon, or a child upgrade's). */
    private String keywords(Selection s) {
        Profile p = s.entry().body().profiles().get(0);
        return TestData.required(new View(fx.model).characteristics(s, p).get(KEYWORDS));
    }

    private List<String> errors() {
        List<String> out = new ArrayList<>();
        new Validator(fx.model, new View(fx.model)).validate().forEach(v -> out.add(v.message()));
        return out;
    }

    private Selection unit(String prefix) {
        Selection s = fx.unit(prefix);
        fx.add(s, s.name() + " child", 1);
        return s;
    }

    // ---- affects

    /** {@code leader own, leader child, bodyguard own, bodyguard child}: whether each gained the keyword. */
    private void affects(String k, String detached, String attached) {
        Fixture f = new Fixture("M7 Associations");
        Selection b1 = f.unit("B1 ");
        Selection l = f.unit(k.toUpperCase() + " ");
        f.add(b1, "B1 Bodyguard child", 1);
        f.add(l, l.name() + " child", 1);
        assertEquals(detached, gained(f, k, l, b1), k + " detached");
        assertTrue(new Associations(f.model, new Evaluator(f.model))
                .attach(l, Associations.of(l).get(0), b1));
        assertEquals(attached, gained(f, k, l, b1), k + " attached");
    }

    private static String gained(Fixture f, String k, Selection l, Selection b1) {
        View view = new View(f.model);
        Selection[] who = {l, l.children().get(0), b1, b1.children().get(0)};
        StringBuilder out = new StringBuilder();
        for (Selection s : who) {
            String got = TestData.required(
                    view.characteristics(s, s.entry().body().profiles().get(0)).get(KEYWORDS));
            out.append(got.contains("FROM-" + k) ? 'x' : '.');
        }
        return out.toString();
    }

    @Test
    void affectsPathsReachWhatNewRecruitReached() {
        affects("la", "x...", "x.x."); // self.group.profiles
        affects("lb", "x...", "x.x."); // group.profiles: the group includes the holder
        affects("lc", "x...", "x.x."); // group.recursive.profiles: children not reached
        affects("ld", ".x..", "...."); // group.entries.profiles: children when alone, nothing when attached
        affects("le", "x...", "x..."); // self.profiles
        affects("lf", ".x..", ".x.."); // entries.profiles
        affects("lg", "x...", "x..."); // profiles: no tokens means self
        affects("lh", ".x..", ".x.."); // entries.recursive.profiles
        affects("li", "xx..", "x.x."); // self.entries.group.profiles
    }

    @Test
    void theGroupIsSymmetricAndEachUnitSeesItsOwnModifierFirst() {
        Selection b = fx.unit("BM ");
        Selection l = fx.unit("LBM ");
        assertEquals("lbm-own, FROM-lbm", keywords(l));
        assertTrue(attach(l, b));
        assertEquals("lbm-own, FROM-lbm, FROM-bm", keywords(l));
        assertEquals("bm-own, FROM-bm, FROM-lbm", keywords(b));
    }

    @Test
    void severalLeadersJoinOneGroup() {
        Selection b6 = fx.unit("B6 ");
        Selection m5 = fx.unit("M5 ");
        Selection m6 = fx.unit("M6 ");
        Selection m7 = fx.unit("M7 ");
        assertTrue(attach(m5, b6));
        assertTrue(attach(m6, b6));
        assertEquals("b6-own, FROM-b6, FROM-m5, FROM-m6", keywords(b6));
        assertEquals("m5-own, FROM-m5, FROM-b6, FROM-m6", keywords(m5));
        assertEquals("m6-own, FROM-m6, FROM-b6, FROM-m5", keywords(m6));
        assertEquals("m7-own", keywords(m7));
        assertTrue(attach(m7, b6));
        assertEquals("m7-own, FROM-b6, FROM-m5, FROM-m6", keywords(m7));
    }

    // ---- traverseAssociationGroup

    private List<String> perks(
            String leaderPrefix, String bodyguardPrefix, int onLeader, int onBodyguard, boolean att) {
        Selection b = fx.unit(bodyguardPrefix);
        Selection l = fx.unit(leaderPrefix);
        if (onLeader > 0) {
            fx.add(l, l.name() + " perk", onLeader);
        }
        if (onBodyguard > 0) {
            fx.add(b, b.name() + " perk", onBodyguard);
        }
        if (att) {
            assertTrue(attach(l, b));
        }
        return errors();
    }

    @Test
    void traverseCountsAcrossTheAttachedGroup() {
        // max 1 upgrade at root-entry: only when attached with a perk on each
        assertEquals(List.of(), new AssociationTest().perks("T1 ", "B5 ", 1, 0, false));
        assertEquals(List.of(), new AssociationTest().perks("T1 ", "B5 ", 1, 1, false));
        assertEquals(List.of("t1"), new AssociationTest().perks("T1 ", "B5 ", 1, 1, true));
        // the same constraint without the flag never fires
        assertEquals(List.of(), new AssociationTest().perks("T2 ", "B5 ", 1, 1, true));
        // scope self behaves alike
        assertEquals(List.of(), new AssociationTest().perks("T3 ", "B5 ", 1, 1, false));
        assertEquals(List.of("t3"), new AssociationTest().perks("T3 ", "B5 ", 1, 1, true));
        // the force is one node for both units, so it is counted once (max 3: 2 + 1 passes, 3 + 1 fails)
        assertEquals(List.of(), new AssociationTest().perks("T9 ", "B5 ", 2, 1, true));
        assertEquals(List.of("t9"), new AssociationTest().perks("T9 ", "B5 ", 3, 1, true));
        // a force-scope constraint fires with or without the attachment
        assertEquals(List.of("t4"), new AssociationTest().perks("T4 ", "B5 ", 1, 1, false));
    }

    @Test
    void traverseWorksForMinimumsToo() {
        // min 2: one perk is short alone, and 1 + 1 is enough when attached
        List<String> alone = new AssociationTest().perks("T5 ", "B5 ", 1, 1, false);
        assertTrue(alone.contains("t5"), alone.toString());
        assertFalse(new AssociationTest().perks("T5 ", "B5 ", 1, 1, true).contains("t5"));
    }

    // ---- association limits

    @Test
    void associationsFieldCountsNothing() {
        // B4 wants min 1 Leader: it fires even with a leader attached; B3 and B6 maxima never fire
        Selection b4 = fx.unit("B4 ");
        Selection m4 = fx.unit("M4 ");
        assertTrue(attach(m4, b4));
        assertEquals(List.of("b4l"), errors());
    }

    @Test
    void maxIsNotEnforcedButReported() {
        Selection m2 = fx.unit("M2 ");
        Selection b1a = fx.unit("B1 ");
        Selection b1b = fx.unit("B1 ");
        Selection b2 = fx.unit("B2 ");
        assertTrue(attach(m2, b1a));
        assertTrue(attach(m2, b1b));
        assertTrue(attach(m2, b2));
        assertEquals(List.of("Units M2 max 2 (B1 or B2) has 1 unit too many selected in Leading m2 (3/2)"), errors());
    }

    @Test
    void minIsReportedUntilAUnitIsAttached() {
        Selection m1 = fx.unit("M1 ");
        Selection b1 = fx.unit("B1 ");
        assertEquals(List.of("Units M1 min 1 max 1 needs 1 more unit selected in Leading m1 (0/1)"), errors());
        assertTrue(attach(m1, b1));
        assertEquals(List.of(), errors());
    }

    @Test
    void aLeaderIsOfferedTheUnitsItsConditionsAllow() {
        Selection m3a = fx.unit("M3 ");
        Selection m3b = fx.unit("M3 ");
        Selection m2 = fx.unit("M2 ");
        Selection b1 = fx.unit("B1 ");
        Selection b2 = fx.unit("B2 ");
        Selection b5 = fx.unit("B5 ");
        Associations a = new Associations(fx.model, new Evaluator(fx.model));
        // a leader with no conditions is offered every other unit, never itself
        assertEquals(
                List.of(m3b, m2, b1, b2, b5),
                a.offered(m3a, Associations.of(m3a).get(0)));
        assertEquals(List.of(b1, b2), a.offered(m2, Associations.of(m2).get(0)));
        assertFalse(a.attach(m2, Associations.of(m2).get(0), b5));
    }
}
