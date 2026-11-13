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
        for (String file : List.of("dup-constraints.roster.json", "extension-tests.roster.json")) {
            DataNode parsed = JSON.parse(resource(file));
            Roster roster = Roster.read(parsed);
            assertEquals(parsed, roster.toNode(), file);
            // and through text, as a file would be written and read back
            assertEquals(parsed, JSON.parse(JSON.write(roster.toNode())), file);
        }
    }

    @Test
    void theRosterCostIsTheSumOfTheSelectionCosts() throws IOException {
        Roster r = Roster.read(JSON.parse(resource("extension-tests.roster.json")));
        Map<String, Double> sums = new LinkedHashMap<>();
        r.forces().forEach(f -> f.selections().forEach(s -> addCosts(s, sums)));
        Map<String, Double> expected = new LinkedHashMap<>();
        r.costs().forEach(c -> expected.put(c.name(), c.value()));
        assertEquals(expected, sums);
        assertEquals(Map.of("pts", 1200.0, "Enh", 2.0), sums);
    }

    private static void addCosts(Roster.Selection s, Map<String, Double> sums) {
        s.costs().forEach(c -> sums.merge(c.name(), c.value() * s.number(), Double::sum));
        s.selections().forEach(child -> addCosts(child, sums));
    }
}
