/* (C) Edward Harman and contributors 2026 */
package org.ethelred.bsdata;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import org.junit.jupiter.api.Test;

class LinkerTest {
    private static final JsonSource JSON = new MinimalJsonSource();

    @Test
    void resolvesAcrossFilesAndReportsTheRest() {
        var a = JSON.parse("{\"catalogue\":{\"id\":\"a\",\"selectionEntries\":[{\"id\":\"b\"}],"
                + "\"entryLinks\":[{\"id\":\"d\",\"targetId\":\"b\"},{\"id\":\"e\",\"targetId\":\"zzz\"}]}}");
        var b = JSON.parse("{\"catalogue\":{\"id\":\"f\",\"entryLinks\":[{\"id\":\"g\",\"targetId\":\"b\"}],"
                + "\"conditions\":[{\"childId\":\"model\"},{\"childId\":\"d\"}]}}");
        var r = Linker.check(Map.of("a.json", a, "b.json", b));
        assertEquals(2, r.files());
        assertEquals(new Linker.Stats(3, 2, 0, 1), r.byField().get("targetId"));
        assertEquals(new Linker.Stats(2, 1, 1, 0), r.byField().get("childId"));
        assertEquals(1, r.unresolved().size());
        assertEquals("zzz", r.unresolved().get(0).value());
    }

    @Test
    void countsIdsSharedBetweenFiles() {
        var a = JSON.parse("{\"x\":{\"id\":\"same\"}}");
        var b = JSON.parse("{\"y\":{\"id\":\"same\"}}");
        assertEquals(1, Linker.check(Map.of("a", a, "b", b)).idsInSeveralFiles());
    }
}
