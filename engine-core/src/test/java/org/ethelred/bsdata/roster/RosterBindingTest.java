/* (C) Edward Harman and contributors 2026 */
package org.ethelred.bsdata.roster;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.ethelred.bsdata.Roster;
import org.ethelred.bsdata.TestData;
import org.ethelred.bsdata.model.Catalogue;
import org.ethelred.bsdata.model.Child;
import org.ethelred.bsdata.model.Entry;
import org.ethelred.bsdata.model.GameData;
import org.ethelred.bsdata.model.Link;
import org.ethelred.bsdata.model.Parts.ForceEntry;
import org.junit.jupiter.api.Test;

/** M1: explicit roster building, and base costs checked against rosters exported from New Recruit. */
class RosterBindingTest {
    private final GameData data = load();

    private static GameData load() {
        try {
            return TestData.gameData();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Map<String, Double> byName(Map<String, Double> byTypeId, GameData data, String file) {
        Map<String, Double> out = new LinkedHashMap<>();
        byTypeId.forEach((id, v) -> {
            var type = data.costType(file, id);
            out.put(type == null ? id : type.name(), v);
        });
        return out;
    }

    @Test
    void anExportedRosterBindsWithoutProblemsAndMatchesNrsCosts() throws IOException {
        Roster file = TestData.roster("m1-costs.roster.json");
        RosterBinder.Bound bound = RosterBinder.bind(data, file);
        assertEquals(List.of(), bound.problems());
        RosterModel model = bound.model();

        // every selection's own cost equals the cost NR exported for it
        Map<String, Map<String, Double>> exported = new LinkedHashMap<>();
        file.forces().forEach(f -> f.selections().forEach(s -> collect(s, exported)));
        assertEquals(7, model.selections().size());
        for (Selection s : model.selections()) {
            Map<String, Double> expected = exported.get(s.id());
            assertEquals(expected, byName(s.costs(), data, s.file()), s.name());
        }
        // and so does the roster total: 5 + 3 + 10 + 3 + 15 + 12 + 2
        assertEquals(Map.of("pts", 50.0, "Enh", 2.0), byName(model.costs(), data, "M1 Costs.json"));
    }

    @Test
    void amountsArePerParentAndTotalsMultiplyDownTheTree() throws IOException {
        RosterModel model =
                RosterBinder.bind(data, TestData.roster("m1-costs.roster.json")).model();
        Selection gun = model.selections().stream()
                .filter(s -> s.name().equals("Gun"))
                .findFirst()
                .orElseThrow();
        assertEquals(2.0, gun.amount());
        assertEquals(6.0, gun.total());
        assertEquals("Trooper", TestData.required(gun.parent()).name());
    }

    @Test
    void aLinkCostReplacesTheTargetCostOfTheSameType() throws IOException {
        RosterModel model =
                RosterBinder.bind(data, TestData.roster("m1-costs.roster.json")).model();
        Selection shared = model.selections().stream()
                .filter(s -> s.name().equals("M1 Shared unit"))
                .findFirst()
                .orElseThrow();
        // the entry costs 20 pts and 2 Enh; its link costs 3 pts
        assertEquals(Map.of("pts", 3.0, "Enh", 2.0), byName(shared.unitCosts(), data, shared.file()));
    }

    @Test
    void aHandBuiltRosterTotalsLikeNr() throws IOException {
        Catalogue m1 = data.catalogues().stream()
                .filter(c -> c.name().equals("M1 Costs"))
                .findFirst()
                .orElseThrow();
        ForceEntry forceEntry =
                TestData.required(data.gameSystem()).forceEntries().get(0);
        RosterModel model = new RosterModel(data, "by hand");
        ForceNode force = model.addForce(forceEntry, m1);

        Link squadLink = m1.rootLinks().stream()
                .filter(l -> l.name().equals("M1 Squad"))
                .findFirst()
                .orElseThrow();
        Selection squad = TestData.required(force.add(squadLink, 1));
        assertEquals(Map.of("pts", 10.0), byName(model.costs(), data, m1.file()));

        Entry squadEntry = squad.entry();
        Link banner = (Link) squadEntry.body().children().stream()
                .filter(c -> c instanceof Link)
                .findFirst()
                .orElseThrow();
        squad.add(banner, 1);
        Entry trooperEntry = child(squadEntry, "Trooper");
        Selection troopers = TestData.required(squad.add(trooperEntry, 3));
        troopers.add(child(trooperEntry, "Gun"), 2);
        Entry options = child(squadEntry, "Options");
        squad.add(child(options, "Opt A"), 2);

        // the point where NR showed the squad at 42: 10 + 3 + 15 + 12 + 2
        assertEquals(Map.of("pts", 42.0), byName(squad.subtreeCosts(), data, m1.file()));
        assertEquals(Map.of("pts", 42.0), byName(model.costs(), data, m1.file()));
    }

    @Test
    void everyPathInTheLargerExportsResolves() throws IOException {
        for (String name : List.of("extension-tests.roster.json", "extension-tests-attached.roster.json")) {
            RosterBinder.Bound bound = RosterBinder.bind(data, TestData.roster(name));
            assertEquals(List.of(), bound.problems(), name);
            assertTrue(bound.model().selections().size() > 20, name);
        }
    }

    /** The first entry or group below {@code parent}, at any depth through plain entries and groups, with this name. */
    private static Entry child(Entry parent, String name) {
        for (Child c : parent.body().children()) {
            if (c instanceof Entry e) {
                if (e.name().equals(name)) {
                    return e;
                }
                try {
                    return child(e, name);
                } catch (IllegalArgumentException notBelowThisOne) {
                    // keep looking
                }
            }
        }
        throw new IllegalArgumentException(name);
    }

    private static void collect(Roster.Selection s, Map<String, Map<String, Double>> out) {
        Map<String, Double> costs = new LinkedHashMap<>();
        s.costs().forEach(c -> costs.put(c.name(), c.value()));
        out.put(s.id(), costs);
        s.selections().forEach(child -> collect(child, out));
    }
}
