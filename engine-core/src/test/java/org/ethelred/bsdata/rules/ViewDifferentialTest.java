/* (C) Edward Harman and contributors 2026 */
package org.ethelred.bsdata.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.ethelred.bsdata.DataNode;
import org.ethelred.bsdata.Roster;
import org.ethelred.bsdata.TestData;
import org.ethelred.bsdata.model.GameData;
import org.ethelred.bsdata.model.Parts.Profile;
import org.ethelred.bsdata.roster.RosterBinder;
import org.ethelred.bsdata.roster.RosterModel;
import org.ethelred.bsdata.roster.Selection;
import org.junit.jupiter.api.Test;

/**
 * The modifier pipeline checked against rosters exported from New Recruit: costs and profile characteristics are worked
 * out by the engine and compared with what NR showed.
 */
class ViewDifferentialTest {
    private static RosterModel bind(String name) throws IOException {
        GameData data = TestData.gameData();
        RosterBinder.Bound b = RosterBinder.bind(data, TestData.roster(name));
        assertEquals(List.of(), b.problems(), name);
        return b.model();
    }

    private static void collectCosts(Roster.Selection s, Map<String, Map<String, Double>> out) {
        Map<String, Double> costs = new LinkedHashMap<>();
        s.costs().forEach(c -> costs.put(c.typeId(), c.value()));
        out.put(s.id(), costs);
        s.selections().forEach(c -> collectCosts(c, out));
    }

    // ---- costs

    @Test
    void everySelectionsCostMatchesTheExport() throws IOException {
        List<String> wrong = new ArrayList<>();
        int compared = 0;
        for (String name : List.of(
                "extension-tests.roster.json", "extension-tests-attached.roster.json", "m1-costs.roster.json")) {
            Roster file = TestData.roster(name);
            Map<String, Map<String, Double>> nr = new LinkedHashMap<>();
            file.forces().forEach(f -> f.selections().forEach(s -> collectCosts(s, nr)));
            RosterModel model = bind(name);
            View view = new View(model);
            for (Selection s : model.selections()) {
                compared++;
                Map<String, Double> expected = TestData.required(nr.get(s.id()));
                Map<String, Double> actual = view.costs(s);
                if (!expected.equals(actual)) {
                    wrong.add(name + " " + s.name() + ": engine " + actual + ", NR " + expected);
                }
            }
            Map<String, Double> total = new LinkedHashMap<>();
            file.costs().forEach(c -> total.put(c.typeId(), c.value()));
            if (!total.equals(view.totalCosts())) {
                wrong.add(name + " roster total: engine " + view.totalCosts() + ", NR " + total);
            }
            assertEquals(
                    List.of(),
                    view.diagnostics().stream()
                            .filter(d -> d.contains("depends"))
                            .toList());
        }
        assertEquals("", String.join("\n", wrong));
        assertTrue(compared >= 50, "selections compared: " + compared);
    }

    @Test
    void theCostModifierUnitsShowTheCostsNrShowed() throws IOException {
        // T10a to T10e: 100 pts base with divide 3, divide 7, multiply 2, floor 150 and ceil 60 (RESULTS.md)
        Fixture f = new Fixture("Extension Tests Catalogue");
        String pts = f.costTypeId("pts");
        double[] expected = {33.33, 14.29, 200, 150, 60};
        String[] names = {"T10a", "T10b", "T10c", "T10d", "T10e"};
        for (int i = 0; i < names.length; i++) {
            Selection s = f.unit(names[i]);
            assertEquals(expected[i], new View(f.model).unitCost(s, pts), 0.01, names[i]);
        }
    }

    // ---- profiles

    @Test
    void everyProfileValueTheEngineHandlesMatchesTheExport() throws IOException {
        Roster file = TestData.roster("extension-tests.roster.json");
        RosterModel model = bind("extension-tests.roster.json");
        Map<String, Map<String, Map<String, String>>> nr = new LinkedHashMap<>();
        file.forces().forEach(f -> f.selections().forEach(s -> collectProfiles(s, nr)));
        List<String> wrong = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        int compared = 0;
        for (Selection s : model.selections()) {
            Map<String, Map<String, String>> byProfile = nr.getOrDefault(s.id(), Map.of());
            for (Profile p : s.entry().body().profiles()) {
                Map<String, String> expected = byProfile.get(p.name());
                if (expected == null) {
                    continue;
                }
                View view = new View(model);
                Map<String, String> actual = view.characteristics(s, p);
                if (!view.diagnostics().isEmpty()) {
                    skipped.add(p.name());
                    continue;
                }
                compared++;
                for (var e : expected.entrySet()) {
                    String got = actual.getOrDefault(e.getKey(), "");
                    if (!got.equals(e.getValue())) {
                        wrong.add(
                                p.name() + " " + e.getKey() + ": engine \"" + got + "\", NR \"" + e.getValue() + "\"");
                    }
                }
            }
        }
        System.out.println("profiles compared " + compared + ", skipped " + skipped.size() + ": " + skipped);
        assertEquals("", String.join("\n", wrong));
        assertTrue(compared > 60, "profiles compared: " + compared);
    }

    private static void collectProfiles(Roster.Selection s, Map<String, Map<String, Map<String, String>>> out) {
        Map<String, Map<String, String>> byProfile = new LinkedHashMap<>();
        for (DataNode p : s.profiles()) {
            if (p instanceof DataNode.Obj o
                    && o.fields().get("name") instanceof DataNode.Str name
                    && o.fields().get("characteristics") instanceof DataNode.Arr chars) {
                Map<String, String> values = new LinkedHashMap<>();
                for (DataNode c : chars.items()) {
                    if (c instanceof DataNode.Obj co && co.fields().get("typeId") instanceof DataNode.Str typeId) {
                        values.put(typeId.value(), co.fields().get("$text") instanceof DataNode.Str t ? t.value() : "");
                    }
                }
                byProfile.put(name.value(), values);
            }
        }
        out.put(s.id(), byProfile);
        s.selections().forEach(c -> collectProfiles(c, out));
    }

    // ---- repeats through the whole pipeline

    @Test
    void repeatCountsThroughTheModifierPipelineMatchTheScopeRoster() throws IOException {
        Roster file = TestData.roster("m2-scopes.roster.json");
        RosterModel model = bind("m2-scopes.roster.json");
        Map<String, Map<String, Map<String, String>>> nr = new LinkedHashMap<>();
        file.forces().forEach(f -> f.selections().forEach(s -> collectProfiles(s, nr)));
        String num = TestData.required(model.data().gameSystem()).profileTypes().get(0).characteristicTypes().stream()
                .filter(c -> c.name().equals("Num"))
                .findFirst()
                .orElseThrow()
                .id();
        List<String> wrong = new ArrayList<>();
        int compared = 0;
        int ancestor = 0;
        for (Selection s : model.selections()) {
            Map<String, Map<String, String>> byProfile = nr.getOrDefault(s.id(), Map.of());
            for (Profile p : s.entry().body().profiles()) {
                Map<String, String> expected = byProfile.get(p.name());
                if (expected == null) {
                    continue;
                }
                var mods = Modifier.of(p.raw().modifiers());
                if (mods.get(0).repeats().isEmpty()) {
                    continue; // a condition-gated profile, checked in the next test
                }
                if (mods.get(0).repeats().get(0).query().scope().equals("ancestor")) {
                    ancestor++; // counting at this scope is not modelled; see ScopeDifferentialTest
                    continue;
                }
                compared++;
                String got = new View(model).characteristics(s, p).get(num);
                String nrValue = TestData.required(expected.get(num));
                if (!nrValue.equals(got)) {
                    wrong.add(s.name() + " " + p.name() + ": engine " + got + ", NR " + nrValue);
                }
            }
        }
        assertEquals("", String.join("\n", wrong));
        assertEquals(1284 - 60, compared);
        assertEquals(60, ancestor);
    }

    // ---- conditions on modifiers, including a scope that resolves to nothing

    @Test
    void conditionGatedModifiersMatchTheScopeRoster() throws IOException {
        Roster file = TestData.roster("m2-scopes.roster.json");
        RosterModel model = bind("m2-scopes.roster.json");
        Map<String, Map<String, Map<String, String>>> nr = new LinkedHashMap<>();
        file.forces().forEach(f -> f.selections().forEach(s -> collectProfiles(s, nr)));
        String num = TestData.required(model.data().gameSystem()).profileTypes().get(0).characteristicTypes().stream()
                .filter(c -> c.name().equals("Num"))
                .findFirst()
                .orElseThrow()
                .id();
        List<String> wrong = new ArrayList<>();
        int compared = 0;
        for (Selection s : model.selections()) {
            Map<String, Map<String, String>> byProfile = nr.getOrDefault(s.id(), Map.of());
            for (Profile p : s.entry().body().profiles()) {
                Map<String, String> expected = byProfile.get(p.name());
                var mods = Modifier.of(p.raw().modifiers());
                if (expected == null || mods.isEmpty() || !mods.get(0).repeats().isEmpty()) {
                    continue;
                }
                Condition c = mods.get(0).conditions().conditions().get(0);
                String got = new View(model).characteristics(s, p).get(num);
                String nrValue = TestData.required(expected.get(num));
                compared++;
                if (!nrValue.equals(got)) {
                    wrong.add(s.name() + " " + c.scope() + " " + c.type() + " " + c.value() + ": NR " + nrValue
                            + ", engine " + got);
                }
            }
        }
        assertEquals(300, compared);
        assertEquals("", String.join("\n", wrong));
    }
}
