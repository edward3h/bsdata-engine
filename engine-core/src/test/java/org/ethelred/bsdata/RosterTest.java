/* (C) Edward Harman and contributors 2026 */
package org.ethelred.bsdata;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Fixtures are rosters exported from New Recruit; see the exported-rosters section of nr-extension-tests. */
class RosterTest {
    private static final MinimalJsonSource JSON = new MinimalJsonSource();

    private static String resource(String name) throws IOException {
        try (InputStream in = RosterTest.class.getResourceAsStream("/rosters/" + name)) {
            if (in == null) {
                throw new IllegalStateException("missing resource " + name);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void readsTheStructureOfASmallRoster() throws IOException {
        Roster r = Roster.read(JSON.parse(resource("dup-constraints.roster.json")));
        assertEquals("d2d", r.name());
        assertEquals("Extension Tests", r.gameSystemName());
        assertEquals(1, r.forces().size());
        Roster.Force force = r.forces().get(0);
        assertEquals("Test Force", force.name());
        assertEquals("Dup Z6", force.catalogueName());
        assertEquals(3, force.selections().size());
        Roster.Selection e1 = force.selections().get(1);
        assertEquals("D2 unit E1", e1.name());
        assertEquals("unit", e1.type());
        assertEquals("entry", e1.from());
        assertEquals(
                List.of("APPLY1: relax Opt E1", "Opt E1"),
                e1.selections().stream().map(Roster.Selection::name).toList());
        // an entry id is a path of ids: the entry link, then its target
        assertEquals(2, e1.entryId().split("::").length);
    }

    @Test
    void writingARosterReproducesTheFile() throws IOException {
        for (String file : List.of(
                "dup-constraints.roster.json",
                "extension-tests.roster.json",
                "extension-tests-attached.roster.json",
                "m8-child-forces.roster.json")) {
            DataNode parsed = JSON.parse(resource(file));
            Roster roster = Roster.read(parsed);
            assertEquals(parsed, roster.toNode(), file);
            // and through text, as a file would be written and read back
            assertEquals(parsed, JSON.parse(JSON.write(roster.toNode())), file);
        }
    }

    @Test
    void theRosterCostIsTheSumOfTheSelectionCosts() throws IOException {
        // each selection's cost is already its total (cost times its total number), so the sum is plain
        Roster r = Roster.read(JSON.parse(resource("m1-costs.roster.json")));
        Map<String, Double> sums = new LinkedHashMap<>();
        r.forces().forEach(f -> f.selections().forEach(s -> addCosts(s, sums)));
        Map<String, Double> expected = new LinkedHashMap<>();
        r.costs().forEach(c -> expected.put(c.name(), c.value()));
        assertEquals(expected, sums);
        assertEquals(Map.of("pts", 50.0, "Enh", 2.0), sums);
    }

    @Test
    void anAttachmentIsStoredOnBothSelections() throws IOException {
        Roster r = Roster.read(JSON.parse(resource("extension-tests-attached.roster.json")));
        List<Roster.Selection> all = new java.util.ArrayList<>();
        r.forces().forEach(f -> f.selections().forEach(s -> flatten(s, all)));
        Roster.Selection leader = all.stream()
                .filter(s -> !s.associations().isEmpty())
                .findFirst()
                .orElseThrow();
        Roster.Selection bodyguard = all.stream()
                .filter(s -> !s.incomingAssociations().isEmpty())
                .findFirst()
                .orElseThrow();
        Roster.Association out = leader.associations().get(0);
        Roster.Association in = bodyguard.incomingAssociations().get(0);
        assertEquals("outgoing", out.type());
        assertEquals("incoming", in.type());
        assertEquals(bodyguard.id(), out.selectionId());
        assertEquals(leader.id(), in.selectionId());
        assertEquals("Leading-unit", out.associationId());
        assertEquals(out.associationId(), in.associationId());
        assertEquals("Leading", out.name());
        assertEquals("group", out.action());
        Double amount = in.amount();
        assertEquals(1.0, amount == null ? -1.0 : amount);
        // only one of the two E leaders is attached, and the other has no association
        assertEquals(1, all.stream().filter(s -> !s.associations().isEmpty()).count());
        assertEquals(
                2, all.stream().filter(s -> s.name().startsWith("E - Leader")).count());
    }

    private static void flatten(Roster.Selection s, List<Roster.Selection> out) {
        out.add(s);
        s.selections().forEach(c -> flatten(c, out));
    }

    private static void addCosts(Roster.Selection s, Map<String, Double> sums) {
        s.costs().forEach(c -> sums.merge(c.name(), c.value(), Double::sum));
        s.selections().forEach(child -> addCosts(child, sums));
    }
}
