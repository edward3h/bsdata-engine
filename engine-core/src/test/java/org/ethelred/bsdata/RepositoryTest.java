/* (C) Edward Harman and contributors 2026 */
package org.ethelred.bsdata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** Mirrors tests D1a to D1g in edward3h/nr-extension-tests, where the expected behaviour was observed in NR. */
class RepositoryTest {
    private static final JsonSource JSON = new MinimalJsonSource();

    private static DataNode gameSystem(String id, String... entryIds) {
        return JSON.parse("""
                {"gameSystem": {"id": "%s", "entries": [%s]}}
                """.formatted(id, entries(entryIds)));
    }

    private static DataNode catalogue(String id, List<String> links, String... entryIds) {
        var linkJson = links.stream().map(l -> """
                        {"id": "cl-%s-%s", "targetId": "%s"}""".formatted(id, l, l)).collect(Collectors.joining(","));
        return JSON.parse("""
                {"catalogue": {"id": "%s", "catalogueLinks": [%s], "entries": [%s]}}
                """.formatted(id, linkJson, entries(entryIds)));
    }

    private static String entries(String... ids) {
        return Arrays.stream(ids).map(id -> """
                        {"id": "%s"}""".formatted(id)).collect(Collectors.joining(","));
    }

    private final Map<String, DataNode> files = Map.of(
            "gs.json", gameSystem("gs", "S2", "G"),
            "X.json", catalogue("X", List.of(), "S", "S2"),
            "Y.json", catalogue("Y", List.of(), "S"),
            "W.json", catalogue("W", List.of("X")),
            "Z1.json", catalogue("Z1", List.of("X", "Y")),
            "Z2.json", catalogue("Z2", List.of("Y", "X")),
            "Z3.json", catalogue("Z3", List.of("X", "Y"), "S"),
            "Z4.json", catalogue("Z4", List.of("X")),
            "Z5.json", catalogue("Z5", List.of()),
            "Z7.json", catalogue("Z7", List.of("W")));

    private final Repository repo = new Repository(files);

    @Test
    void firstCatalogueLinkWins() {
        assertEquals("X.json", repo.resolve("Z1.json", "S"));
        assertEquals("Y.json", repo.resolve("Z2.json", "S"));
    }

    @Test
    void ownCopyBeatsLinkedCopies() {
        assertEquals("Z3.json", repo.resolve("Z3.json", "S"));
    }

    @Test
    void gameSystemCopyBeatsLinkedCatalogue() {
        assertEquals("gs.json", repo.resolve("Z4.json", "S2"));
        assertEquals("X.json", repo.resolve("Z4.json", "S"));
    }

    @Test
    void anIdThatIsNotReachableDoesNotResolve() {
        assertNull(repo.resolve("Z5.json", "S"));
        assertEquals("gs.json", repo.resolve("Z5.json", "G"));
    }

    @Test
    void catalogueLinksAreFollowedTransitively() {
        assertEquals("X.json", repo.resolve("Z7.json", "S"));
        assertEquals(List.of("Z7.json", "gs.json", "W.json", "X.json"), repo.scope("Z7.json"));
    }

    @Test
    void theGameSystemSeesOnlyItself() {
        assertNull(repo.resolve("gs.json", "S"));
        assertEquals(List.of("gs.json"), repo.scope("gs.json"));
    }

    @Test
    void countsIdsDefinedInSeveralFiles() {
        // S is in X, Y and Z3; S2 is in gs and X
        assertEquals(2, repo.idsInSeveralFiles());
    }
}
