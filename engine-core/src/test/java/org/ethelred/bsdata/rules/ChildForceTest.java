/* (C) Edward Harman and contributors 2026 */
package org.ethelred.bsdata.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.ethelred.bsdata.TestData;
import org.ethelred.bsdata.model.Catalogue;
import org.ethelred.bsdata.model.GameData;
import org.ethelred.bsdata.model.Link;
import org.ethelred.bsdata.model.Parts.ForceEntry;
import org.ethelred.bsdata.roster.ForceNode;
import org.ethelred.bsdata.roster.RosterBinder;
import org.ethelred.bsdata.roster.RosterModel;
import org.ethelred.bsdata.roster.Selection;
import org.junit.jupiter.api.Test;

/**
 * Compares the engine with what New Recruit did for the synthetic {@code M8 Child Forces} catalogue: a Parent force
 * with Child Force A (which has a Grandchild force) and Child Force B.
 */
class ChildForceTest {
    private static final String KEYWORDS = "a2d2-6932-5906-9eaf";

    /** A roster with the force tree P { A { G }, B } and a way to put units in the forces. */
    private static final class Tree {
        final GameData data;
        final Catalogue catalogue;
        final RosterModel model;
        final Map<String, ForceNode> forces = new LinkedHashMap<>();

        Tree() {
            try {
                data = TestData.gameData();
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
            catalogue = data.catalogues().stream()
                    .filter(c -> c.name().equals("M8 Child Forces"))
                    .findFirst()
                    .orElseThrow();
            model = new RosterModel(data, "test");
            ForceEntry parent = catalogue.forceEntries().get(0);
            ForceNode p = model.addForce(parent, catalogue);
            forces.put("P", p);
            ForceEntry a = entry(parent, "Child Force A");
            ForceNode fa = model.addChildForce(p, a, catalogue);
            forces.put("A", fa);
            forces.put("G", model.addChildForce(fa, a.children().get(0), catalogue));
            forces.put("B", model.addChildForce(p, entry(parent, "Child Force B"), catalogue));
        }

        private static ForceEntry entry(ForceEntry parent, String name) {
            return parent.children().stream()
                    .filter(f -> f.name().equals(name))
                    .findFirst()
                    .orElseThrow();
        }

        ForceNode force(String key) {
            return TestData.required(forces.get(key));
        }

        Selection add(String force, String unitPrefix) {
            for (Link l : catalogue.rootLinks()) {
                var target = data.target(catalogue.file(), l);
                if (target != null && target.entry().name().startsWith(unitPrefix + " ")) {
                    return TestData.required(force(force).add(l, 1));
                }
            }
            throw new IllegalArgumentException(unitPrefix);
        }

        List<String> errors() {
            List<String> out = new ArrayList<>();
            new Validator(model, new View(model)).validate().forEach(v -> out.add(v.message()));
            return out;
        }

        boolean gained(Selection s) {
            View view = new View(model);
            return TestData.required(
                            view.characteristics(s, s.entry().body().profiles().get(0))
                                    .get(KEYWORDS))
                    .contains("IF-");
        }
    }

    private static List<String> errors(String unit, String in, String... fillers) {
        Tree t = new Tree();
        t.add(in, unit);
        for (String f : fillers) {
            t.add(f, "F");
        }
        return t.errors();
    }

    @Test
    void theTreeIsNestedAndTotalsIncludeChildForces() {
        Tree t = new Tree();
        assertEquals(List.of(t.force("P")), t.model.forces());
        assertEquals(
                List.of("P", "A", "G", "B"),
                t.model.allForces().stream().map(f -> key(t, f)).toList());
        assertEquals(t.force("A"), t.force("G").parent());
        t.add("P", "F");
        t.add("A", "CA");
        t.add("G", "F");
        t.add("B", "F");
        // observed: the parent force's cost includes its descendants
        assertEquals(Map.of(costType(t), 40.0), t.force("P").costs());
        assertEquals(Map.of(costType(t), 20.0), t.force("A").costs());
        assertEquals(40.0, TestData.required(new View(t.model).totalCosts().get(costType(t))));
    }

    private static String key(Tree t, ForceNode f) {
        return t.forces.entrySet().stream()
                .filter(e -> e.getValue() == f)
                .findFirst()
                .orElseThrow()
                .getKey();
    }

    private static String costType(Tree t) {
        return t.catalogue.costTypes().isEmpty()
                ? TestData.required(t.data.gameSystem()).costTypes().get(0).id()
                : t.catalogue.costTypes().get(0).id();
    }

    // ---- counting selections

    @Test
    void selectionCountsReachChildForcesOnlyWhenAsked() {
        // CA max 1 unit in the force; CB the same with includeChildForces; CF leaves it unset; CC counts the roster
        assertEquals(List.of(), errors("CA", "P", "A"));
        assertEquals(List.of("cb"), errors("CB", "P", "A"));
        assertEquals(List.of(), errors("CF", "P", "A"));
        assertEquals(List.of("cc"), errors("CC", "P", "A"));
        assertEquals(List.of("cb"), errors("CB", "P", "G")); // all descendants
        assertEquals(List.of("cb"), errors("CB", "P", "B"));
        assertEquals(List.of(), errors("CA", "P", "G"));
        // the other way up, or sideways, nothing is reached
        assertEquals(List.of(), errors("CB", "A", "P"));
        assertEquals(List.of("cc"), errors("CC", "A", "P"));
        assertEquals(List.of("cb"), errors("CB", "A", "G"));
        assertEquals(List.of(), errors("CB", "A", "B"));
        assertEquals(List.of(), errors("CB", "G", "A"));
        assertEquals(List.of("cc"), errors("CC", "G", "A"));
    }

    // ---- counting forces

    @Test
    void forceCountsAreDirectChildrenOrAllDescendants() {
        // G<n>[ny] is max n forces; "y" includes child forces (all levels), "n" only the direct children. Own force
        // is never counted: P has A, B and G below it
        assertEquals(List.of("g1n"), errors("G1n", "P"));
        assertEquals(List.of(), errors("G2n", "P"));
        assertEquals(List.of("g1y"), errors("G1y", "P"));
        assertEquals(List.of("g2y"), errors("G2y", "P"));
        assertEquals(List.of(), errors("G3y", "P"));
        assertEquals(List.of(), errors("G1n", "A")); // A has the one child G
        assertEquals(List.of(), errors("G1y", "A"));
        for (String leaf : List.of("G", "B")) {
            assertEquals(List.of(), errors("G1y", leaf));
        }
        // by force entry: HA max 0 of Child Force A; HB with child forces; HC max 0 of Grandchild, with child forces
        assertEquals(List.of("ha"), errors("HA", "P"));
        assertEquals(List.of(), errors("HA", "A"));
        assertEquals(List.of("hb"), errors("HB", "P"));
        assertEquals(List.of("hc"), errors("HC", "P"));
        assertEquals(List.of("hc"), errors("HC", "A"));
        assertEquals(List.of(), errors("HC", "G"));
    }

    // ---- conditions

    @Test
    void conditionsSeeTheForceTree() {
        // instanceOf with scope force is the nearest force; with scope ancestor, any force on the way up
        assertEquals("P", where("MC", "P", "A", "G", "B"));
        assertEquals("A", where("MB", "P", "A", "G", "B"));
        assertEquals("AG", where("MD", "P", "A", "G", "B"));
        // atLeast 1 forces of Child Force A counted with child forces: only the parent has one below it
        assertEquals("P", where("MA", "P", "A", "G", "B"));
    }

    /** The keys of the forces in which a unit gains the modifier's keyword, when it is added there. */
    private static String where(String unit, String... forces) {
        StringBuilder out = new StringBuilder();
        for (String f : forces) {
            Tree t = new Tree();
            if (t.gained(t.add(f, unit))) {
                out.append(f);
            }
        }
        return out.toString();
    }

    @Test
    void unitCountsInConditionsFollowTheSameRules() {
        // ME counts F in its own force; MF in the roster; MG in its force and the child forces below
        assertEquals("", gained("ME", "A", "P")); // F in the parent is not seen from a child
        assertEquals("Y", gained("MF", "A", "P"));
        assertEquals("Y", gained("MF", "P", "G"));
        assertEquals("Y", gained("MG", "P", "G"));
        assertEquals("Y", gained("MG", "A", "G"));
        assertEquals("", gained("MG", "A", "P"));
        assertEquals("", gained("MG", "A", "B"));
        assertEquals("", gained("MG", "G", "A"));
        assertEquals("", gained("ME", "P", "A"));
    }

    private static String gained(String unit, String in, String fillerIn) {
        Tree t = new Tree();
        Selection s = t.add(in, unit);
        t.add(fillerIn, "F");
        return t.gained(s) ? "Y" : "";
    }

    // ---- roster files

    @Test
    void anExportedRosterBindsWithItsNestedForces() throws IOException {
        Tree t = new Tree();
        RosterBinder.Bound bound = RosterBinder.bind(t.data, TestData.roster("m8-child-forces.roster.json"));
        assertEquals(List.of(), bound.problems());
        RosterModel m = bound.model();
        assertEquals(1, m.forces().size());
        assertEquals(
                List.of("Parent Force", "Child Force A", "Grandchild Force", "Child Force B"),
                m.allForces().stream().map(f -> f.entry().name()).toList());
        assertEquals(
                List.of("F Filler", "CA max1 force selections", "F Filler", "F Filler"),
                m.selections().stream().map(Selection::name).toList());
        // the same validation New Recruit showed: CC counts four in the roster, but the exported units are fine
        assertEquals(List.of(), new ChildForceTest().errorsOf(m));
    }

    private List<String> errorsOf(RosterModel m) {
        List<String> out = new ArrayList<>();
        new Validator(m, new View(m)).validate().forEach(v -> out.add(v.message()));
        return out;
    }
}
